package com.example.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.rag.entity.Document;
import com.example.rag.mapper.DocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 浏览量定时落库服务。
 *
 * <p>详情接口每次访问通过 Redis HINCRBY 原子自增（低延迟），
 * 本服务定时把 Redis 中的最新计数批量回写 MySQL document.view_count，
 * 保证统计报表/列表展示的浏览量不丢、不重。</p>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>SCAN 迭代（非 KEYS），避免阻塞 Redis 单线程；</li>
 *   <li>幂等：MySQL 写入 Redis 当前绝对值，重复执行结果一致；</li>
 *   <li>单条失败仅告警，不影响其余键；</li>
 *   <li>间隔可配：view-count.sync-interval-ms，默认 5 分钟。</li>
 * </ul>
 *
 * @author knowledge-rag-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ViewCountSyncService {

    /** 文档缓存哈希 Key 前缀（与 DocumentServiceImpl / DocumentAsyncServiceImpl 保持一致） */
    private static final String DOC_CACHE_KEY_PREFIX = "doc:";

    /** 文档缓存哈希中存放浏览量的字段名 */
    private static final String FIELD_VIEW_COUNT = "viewCount";

    private final StringRedisTemplate redisTemplate;
    private final DocumentMapper documentMapper;

    @Scheduled(fixedDelayString = "${view-count.sync-interval-ms:300000}")
    public void syncViewCountsToDb() {
        long startTime = System.currentTimeMillis();
        List<String> keys = collectDocCacheKeys();
        if (keys.isEmpty()) {
            return;
        }

        int updated = 0;
        for (String key : keys) {
            try {
                Long docId = parseDocId(key);
                if (docId == null) {
                    continue;
                }
                String viewCountStr = (String) redisTemplate.opsForHash().get(key, FIELD_VIEW_COUNT);
                if (viewCountStr == null) {
                    continue;
                }
                int viewCount = Integer.parseInt(viewCountStr);
                if (viewCount <= 0) {
                    continue;
                }

                UpdateWrapper<Document> uw = new UpdateWrapper<>();
                uw.eq("id", docId).set("view_count", viewCount);
                documentMapper.update(null, uw);
                updated++;
            } catch (Exception e) {
                log.warn("浏览量落库失败（忽略）: key={}", key, e);
            }
        }

        log.info("浏览量落库完成: keys={}, updated={}, cost={}ms",
                keys.size(), updated, System.currentTimeMillis() - startTime);
    }

    // ==================== 私有方法 ====================

    private List<String> collectDocCacheKeys() {
        List<String> keys = new ArrayList<>();
        try {
            ScanOptions options = ScanOptions.scanOptions()
                    .match(DOC_CACHE_KEY_PREFIX + "*")
                    .count(200)
                    .build();
            try (Cursor<String> cursor = redisTemplate.scan(options)) {
                while (cursor.hasNext()) {
                    keys.add(cursor.next());
                }
            }
        } catch (Exception e) {
            log.warn("SCAN doc:* 失败（忽略）: {}", e.getMessage());
        }
        return keys;
    }

    private Long parseDocId(String key) {
        if (key == null || !key.startsWith(DOC_CACHE_KEY_PREFIX)) {
            return null;
        }
        try {
            return Long.parseLong(key.substring(DOC_CACHE_KEY_PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
