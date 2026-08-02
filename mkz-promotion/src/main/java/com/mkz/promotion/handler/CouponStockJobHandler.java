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

                    // 执行预热脚本（issueEndTime 统一存 epoch 秒，与 Lua 脚本 time()[1] 口径一致）
                    Long result = redisTemplate.execute(
                            PRELOAD_SCRIPT,
                            List.of(key),
                            String.valueOf(coupon.getTotalNum()),
                            String.valueOf(coupon.getUserLimit()),
                            String.valueOf(coupon.getIssueEndTime() != null ?
                                    com.mkz.common.utils.DateUtils.toEpochMilli(coupon.getIssueEndTime()) / 1000 : 9999999999L)
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
                    
                    // 获取Redis中的剩余库存
                    String remainingStr = redisTemplate.opsForHash().get(key, "totalNum") != null ?
                            redisTemplate.opsForHash().get(key, "totalNum").toString() : null;

                    if (remainingStr != null) {
                        int remaining = Integer.parseInt(remainingStr);
                        // 由剩余库存反推已发放数量：issued = totalNum - remaining
                        // 只同步 issue_num，绝不覆盖不可变的 total_num——
                        // 否则 total_num 被收缩后 issue_num<total_num 乐观校验将导致后续发放全拒
                        if (remaining >= 0 && remaining <= coupon.getTotalNum()) {
                            int issued = coupon.getTotalNum() - remaining;
                            if (issued != coupon.getIssueNum()) {
                                couponService.lambdaUpdate()
                                        .set(Coupon::getIssueNum, issued)
                                        .eq(Coupon::getId, coupon.getId())
                                        .update();
                                syncCount++;
                            }
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
