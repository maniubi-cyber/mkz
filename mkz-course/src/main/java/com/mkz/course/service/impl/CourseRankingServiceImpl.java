package com.mkz.course.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mkz.course.constants.RankingConstants;
import com.mkz.course.domain.po.CourseRanking;
import com.mkz.course.domain.vo.CourseRankingVO;
import com.mkz.course.mapper.CourseRankingMapper;
import com.mkz.course.service.ICourseRankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 课程排行榜服务实现类
 * 
 * 基于Redis ZSet实现实时排序，按赛季做冷热分层：
 * - 当季热数据常驻Redis支撑毫秒级查询
 * - 往季冷数据归档至MySQL释放内存
 * - 由XXL-Job定期归档
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CourseRankingServiceImpl implements ICourseRankingService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final CourseRankingMapper courseRankingMapper;

    @Override
    public void updateScore(Long courseId, double score) {
        // 写入当前赛季排行榜（与查询 getCurrentSeasonId() 的 key 保持一致，避免读写分裂）
        String key = RankingConstants.COURSE_RANKING_KEY_PREFIX + getCurrentSeasonId();
        redisTemplate.opsForZSet().add(
                key,
                courseId.toString(),
                score
        );
        // 设置过期时间
        redisTemplate.expire(
                key,
                RankingConstants.CURRENT_SEASON_EXPIRE_DAYS,
                TimeUnit.DAYS
        );
    }

    @Override
    public void updateScore(Long courseId, double score, String seasonId) {
        String key = RankingConstants.COURSE_RANKING_KEY_PREFIX + seasonId;
        redisTemplate.opsForZSet().add(key, courseId.toString(), score);
        redisTemplate.expire(key, RankingConstants.CURRENT_SEASON_EXPIRE_DAYS, TimeUnit.DAYS);
    }

    @Override
    public List<CourseRankingVO> getCurrentSeasonRanking(int topN) {
        return getSeasonRanking(getCurrentSeasonId(), topN);
    }

    @Override
    public List<CourseRankingVO> getSeasonRanking(String seasonId, int topN) {
        String key = RankingConstants.COURSE_RANKING_KEY_PREFIX + seasonId;
        
        // 从Redis ZSet获取Top N（倒序，分数从高到低）
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .reverseRangeWithScores(key, 0, topN - 1);

        if (tuples == null || tuples.isEmpty()) {
            // Redis未命中，从MySQL查询冷数据
            return getRankingFromDb(seasonId, topN);
        }

        // 转换为VO列表
        List<CourseRankingVO> result = new ArrayList<>();
        long rank = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            Long courseId;
            try {
                courseId = Long.valueOf(tuple.getValue());
            } catch (NumberFormatException e) {
                // 防御：跳过非法成员（如历史遗留的占位数据），避免排行榜接口崩溃
                log.warn("排行榜存在非法成员，跳过: seasonId={}, member={}", seasonId, tuple.getValue());
                continue;
            }
            CourseRankingVO vo = new CourseRankingVO();
            vo.setCourseId(courseId);
            vo.setScore(tuple.getScore());
            vo.setRank(rank++);
            vo.setSeasonId(seasonId);
            result.add(vo);
        }

        return result;
    }

    @Override
    public Long getCourseRank(Long courseId) {
        return getCourseRank(courseId, getCurrentSeasonId());
    }

    @Override
    public Long getCourseRank(Long courseId, String seasonId) {
        String key = RankingConstants.COURSE_RANKING_KEY_PREFIX + seasonId;
        // reverseRank返回的是倒序排名（0开始），需要+1转换为从1开始的排名
        Long rank = redisTemplate.opsForZSet().reverseRank(key, courseId.toString());
        return rank != null ? rank + 1 : null;
    }

    @Override
    public boolean createNewSeason(String seasonId) {
        try {
            // 保存赛季信息
            Map<String, String> seasonInfo = new HashMap<>();
            seasonInfo.put("seasonId", seasonId);
            seasonInfo.put("createdAt", LocalDateTime.now().toString());
            seasonInfo.put("status", "active");
            
            redisTemplate.opsForHash().putAll(
                    RankingConstants.SEASON_INFO_KEY + ":" + seasonId,
                    seasonInfo
            );
            
            log.info("创建新赛季成功，seasonId: {}", seasonId);
            return true;
        } catch (Exception e) {
            log.error("创建新赛季失败，seasonId: {}", seasonId, e);
            return false;
        }
    }

    @Override
    public int archiveSeasonData(String seasonId) {
        String key = RankingConstants.COURSE_RANKING_KEY_PREFIX + seasonId;

        // 获取赛季全量数据（降序：分数从高到低，用于计算名次）
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .reverseRangeWithScores(key, 0, -1);

        if (tuples == null || tuples.isEmpty()) {
            log.warn("赛季数据为空，无需归档，seasonId: {}", seasonId);
            return 0;
        }

        // 批量写入MySQL（按降序赋 rank：1, 2, 3...）
        List<CourseRanking> records = new ArrayList<>();
        int rank = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            Long courseId;
            try {
                courseId = Long.valueOf(tuple.getValue());
            } catch (NumberFormatException e) {
                // 防御：跳过非法成员（如历史遗留的占位数据），避免归档任务崩溃
                log.warn("归档跳过非法成员: seasonId={}, member={}", seasonId, tuple.getValue());
                continue;
            }
            CourseRanking ranking = new CourseRanking();
            ranking.setCourseId(courseId);
            ranking.setTotalScore(tuple.getScore());
            ranking.setSeasonId(seasonId);
            ranking.setRank(rank++);
            ranking.setCreatedAt(LocalDateTime.now());
            records.add(ranking);
        }

        // 批量插入（一次性写入，替代逐条insert，提升归档性能）
        if (!records.isEmpty()) {
            courseRankingMapper.insertBatch(records);
        }

        // 删除Redis中的历史赛季数据，释放内存
        redisTemplate.delete(key);
        
        log.info("赛季数据归档完成，seasonId: {}, 记录数: {}", seasonId, records.size());
        return records.size();
    }

    @Override
    public boolean switchCurrentSeason(String newSeasonId) {
        try {
            // 1. 将旧赛季数据归档
            String oldSeasonId = getCurrentSeasonId();
            if (!"current".equals(oldSeasonId)) {
                archiveSeasonData(oldSeasonId);
            }

            // 2. 设置新赛季ID
            redisTemplate.opsForValue().set(
                    RankingConstants.CURRENT_SEASON_ID,
                    newSeasonId,
                    RankingConstants.CURRENT_SEASON_EXPIRE_DAYS * 2,
                    TimeUnit.DAYS
            );

            // 3. 创建新赛季排行榜（ZSet 无需初始化，首次写分数时自动建 key；
            //    不再塞 "init" 占位成员——它会污染排行榜读取与归档（Long.valueOf 崩溃））
            log.info("切换当前赛季成功，newSeasonId: {}", newSeasonId);
            return true;
        } catch (Exception e) {
            log.error("切换当前赛季失败，newSeasonId: {}", newSeasonId, e);
            return false;
        }
    }

    /**
     * 获取当前赛季ID
     */
    private String getCurrentSeasonId() {
        String seasonId = redisTemplate.opsForValue().get(RankingConstants.CURRENT_SEASON_ID);
        return seasonId != null ? seasonId : "current";
    }

    /**
     * 从MySQL获取冷数据排行榜
     */
    private List<CourseRankingVO> getRankingFromDb(String seasonId, int topN) {
        List<CourseRanking> records = courseRankingMapper.selectBySeasonId(seasonId, topN);
        
        if (records.isEmpty()) {
            return Collections.emptyList();
        }

        return records.stream()
                .map(ranking -> {
                    CourseRankingVO vo = new CourseRankingVO();
                    vo.setCourseId(ranking.getCourseId());
                    vo.setScore(ranking.getTotalScore());
                    vo.setRank(ranking.getRank().longValue());
                    vo.setSeasonId(seasonId);
                    return vo;
                })
                .collect(Collectors.toList());
    }
}
