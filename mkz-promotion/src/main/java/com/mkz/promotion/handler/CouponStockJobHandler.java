package com.mkz.promotion.handler;

import com.mkz.promotion.domain.po.Coupon;
import com.mkz.promotion.enums.CouponStatus;
import com.mkz.promotion.service.ICouponService;
import com.mkz.promotion.constants.PromotionConstants;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 优惠券库存定时任务
 * 
 * 1. 库存预热：将进行中的优惠券库存信息加载到Redis
 * 2. 库存同步：将Redis中的库存信息同步到数据库
 * 3. 确保Redis和数据库的库存一致性
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CouponStockJobHandler {

    private final ICouponService couponService;
    private final StringRedisTemplate redisTemplate;

    private static final RedisScript<Long> PRELOAD_SCRIPT;

    static {
        PRELOAD_SCRIPT = RedisScript.of(
                new ClassPathResource("lua/preload_coupon.lua"), Long.class);
    }

    /**
     * 优惠券库存预热
     * 
     * 定期将进行中的优惠券库存信息加载到Redis
     * 避免缓存击穿
     */
    @XxlJob("couponStockPreloadJobHandler")
    public void preloadCouponStock() {
        log.info("开始执行优惠券库存预热任务");

        try {
            // 1. 查询进行中的优惠券
            List<Coupon> issuingCoupons = couponService.lambdaQuery()
                    .eq(Coupon::getStatus, CouponStatus.ISSUING)
                    .list();

            if (issuingCoupons.isEmpty()) {
                log.info("没有进行中的优惠券，无需预热");
                return;
            }

            int successCount = 0;
            // 2. 逐个预热到Redis
            for (Coupon coupon : issuingCoupons) {
                try {
                    String key = PromotionConstants.COUPON_CACHE_KEY_PREFIX + coupon.getId();
                    
                    // 检查是否已缓存
                    Boolean exists = redisTemplate.hasKey(key);
                    if (Boolean.TRUE.equals(exists)) {
                        continue;
                    }

                    // 执行预热脚本
                    Long result = redisTemplate.execute(
                            PRELOAD_SCRIPT,
                            List.of(key),
                            String.valueOf(coupon.getTotalNum()),
                            String.valueOf(coupon.getUserLimit()),
                    String.valueOf(coupon.getIssueEndTime() != null ? 
                            coupon.getIssueEndTime().toString() : "9999999999")
                    );

                    if (result != null && result == 0) {
                        successCount++;
                    }
                } catch (Exception e) {
                    log.error("预热优惠券库存失败，couponId: {}", coupon.getId(), e);
                }
            }

            log.info("优惠券库存预热完成，共处理 {} 个优惠券，成功 {} 个", 
                    issuingCoupons.size(), successCount);

        } catch (Exception e) {
            log.error("优惠券库存预热任务执行异常", e);
            XxlJobHelper.handleFail("库存预热失败: " + e.getMessage());
        }
    }

    /**
     * 优惠券库存同步
     * 
     * 定期将Redis中的库存扣减信息同步到数据库
     * 确保Redis和数据库的一致性
     */
    @XxlJob("couponStockSyncJobHandler")
    public void syncCouponStock() {
        log.info("开始执行优惠券库存同步任务");

        try {
            // 1. 查询进行中的优惠券
            List<Coupon> issuingCoupons = couponService.lambdaQuery()
                    .eq(Coupon::getStatus, CouponStatus.ISSUING)
                    .list();

            int syncCount = 0;
            // 2. 逐个同步库存
            for (Coupon coupon : issuingCoupons) {
                try {
                    String key = PromotionConstants.COUPON_CACHE_KEY_PREFIX + coupon.getId();
                    
                    // 获取Redis中的库存信息
                    String totalNumStr = redisTemplate.opsForHash().get(key, "totalNum") != null ? 
                            redisTemplate.opsForHash().get(key, "totalNum").toString() : null;
                    
                    if (totalNumStr != null) {
                        int redisTotalNum = Integer.parseInt(totalNumStr);
                        
                        // 如果Redis库存与数据库不一致，同步到数据库
                        if (redisTotalNum != coupon.getTotalNum()) {
                            coupon.setTotalNum(redisTotalNum);
                            couponService.updateById(coupon);
                            syncCount++;
                        }
                    }
                } catch (Exception e) {
                    log.error("同步优惠券库存失败，couponId: {}", coupon.getId(), e);
                }
            }

            log.info("优惠券库存同步完成，共同步 {} 个优惠券", syncCount);

        } catch (Exception e) {
            log.error("优惠券库存同步任务执行异常", e);
            XxlJobHelper.handleFail("库存同步失败: " + e.getMessage());
        }
    }
}
