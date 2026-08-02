package com.mkz.course.handler;

import com.mkz.course.constants.RankingConstants;
import com.mkz.course.service.ICourseRankingService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 排行榜归档定时任务
 * 
 * 定期将历史赛季的热数据归档至MySQL，释放Redis内存
 * - 由XXL-Job定期执行
 * - 当季热数据常驻Redis
 * - 往季冷数据归档至MySQL
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RankingArchiveJobHandler {

    private final RedisTemplate<String, String> redisTemplate;
    private final ICourseRankingService courseRankingService;

    /**
     * 归档历史赛季排行榜数据
     * 
     * 定时扫描所有历史赛季数据，将超过保留期的数据归档到MySQL
     */
    @XxlJob("rankingArchiveJobHandler")
    public void archiveHistoryRanking() {
        log.info("开始执行排行榜归档任务");

        try {
            // 1. 获取当前赛季ID
            String currentSeasonId = redisTemplate.opsForValue().get(RankingConstants.CURRENT_SEASON_ID);
            if (currentSeasonId == null) {
                currentSeasonId = "current";
            }

            // 2. 使用 scan 游标增量扫描，避免 keys 全量扫描阻塞 Redis
            List<String> archivedSeasons = new ArrayList<>();
            ScanOptions options = ScanOptions.scanOptions()
                    .match(RankingConstants.COURSE_RANKING_KEY_PREFIX + "*")
                    .count(100)
                    .build();
            try (Cursor<String> cursor = redisTemplate.scan(options)) {
                while (cursor.hasNext()) {
                    String key = cursor.next();
                    // 从key中提取赛季ID
                    String seasonId = key.substring(RankingConstants.COURSE_RANKING_KEY_PREFIX.length());

                    // 跳过当前赛季（热数据常驻Redis）
                    if (currentSeasonId.equals(seasonId)) {
                        continue;
                    }
                    // 跳过默认"current"字面量：该 key 可能是默认赛季的实时数据，
                    // 若被归档将删除实时榜数据且重复入库（history 表无唯一键）
                    if ("current".equals(seasonId)) {
                        continue;
                    }

                    // 归档往季冷数据；archiveSeasonData 完成后会删除该 key，天然幂等
                    int count = courseRankingService.archiveSeasonData(seasonId);
                    if (count > 0) {
                        archivedSeasons.add(seasonId);
                        log.info("赛季 {} 数据归档完成，记录数: {}", seasonId, count);
                    }
                }
            }

            log.info("排行榜归档任务完成，共归档 {} 个赛季数据", archivedSeasons.size());

        } catch (Exception e) {
            log.error("排行榜归档任务执行异常", e);
            XxlJobHelper.handleFail("归档失败: " + e.getMessage());
        }
    }

    /**
     * 清理过期赛季信息
     * 
     * 定期清理Redis中过期的赛季元数据
     */
    @XxlJob("rankingCleanJobHandler")
    public void cleanExpiredSeasonInfo() {
        log.info("开始清理过期赛季信息");

        try {
            List<String> deletedKeys = new ArrayList<>();
            ScanOptions options = ScanOptions.scanOptions()
                    .match(RankingConstants.SEASON_INFO_KEY + ":*")
                    .count(100)
                    .build();
            try (Cursor<String> cursor = redisTemplate.scan(options)) {
                while (cursor.hasNext()) {
                    String key = cursor.next();
                    Long expire = redisTemplate.getExpire(key);
                    if (expire == null || expire <= 0) {
                        redisTemplate.delete(key);
                        deletedKeys.add(key);
                    }
                }
            }

            log.info("清理过期赛季信息完成，共清理 {} 条", deletedKeys.size());
        } catch (Exception e) {
            log.error("清理过期赛季信息异常", e);
        }
    }
}
