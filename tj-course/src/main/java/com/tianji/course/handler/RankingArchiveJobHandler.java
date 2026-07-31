package com.tianji.course.handler;

import com.tianji.course.constants.RankingConstants;
import com.tianji.course.service.ICourseRankingService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

            // 2. 扫描所有赛季排行榜Key
            Set<String> keys = redisTemplate.keys(RankingConstants.COURSE_RANKING_KEY_PREFIX + "*");
            
            if (keys == null || keys.isEmpty()) {
                log.info("没有需要归档的排行榜数据");
                return;
            }

            // 3. 遍历并归档历史赛季数据
            List<String> archivedSeasons = new ArrayList<>();
            for (String key : keys) {
                // 从key中提取赛季ID
                String seasonId = key.substring(RankingConstants.COURSE_RANKING_KEY_PREFIX.length());
                
                // 跳过当前赛季
                if (currentSeasonId.equals(seasonId)) {
                    continue;
                }

                // 检查数据是否已过期（超过24小时）
                Long expire = redisTemplate.getExpire(key);
                if (expire != null && expire > 0) {
                    // 数据仍有过期时间，说明是活跃数据，跳过
                    continue;
                }

                // 归档数据
                int count = courseRankingService.archiveSeasonData(seasonId);
                if (count > 0) {
                    archivedSeasons.add(seasonId);
                    log.info("赛季 {} 数据归档完成，记录数: {}", seasonId, count);
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
            Set<String> keys = redisTemplate.keys(RankingConstants.SEASON_INFO_KEY + ":*");
            if (keys == null || keys.isEmpty()) {
                return;
            }

            List<String> deletedKeys = new ArrayList<>();
            for (String key : keys) {
                Long expire = redisTemplate.getExpire(key);
                if (expire == null || expire <= 0) {
                    redisTemplate.delete(key);
                    deletedKeys.add(key);
                }
            }

            log.info("清理过期赛季信息完成，共清理 {} 条", deletedKeys.size());
        } catch (Exception e) {
            log.error("清理过期赛季信息异常", e);
        }
    }
}
