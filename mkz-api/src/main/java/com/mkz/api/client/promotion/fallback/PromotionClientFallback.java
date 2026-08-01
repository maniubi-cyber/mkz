package com.mkz.api.client.promotion.fallback;


import com.mkz.api.client.promotion.PromotionClient;

import com.mkz.api.dto.promotion.CouponDetailSimpleVO;
import com.mkz.api.dto.promotion.CouponDiscountDTO;
import com.mkz.api.dto.promotion.OrderCouponDTO;
import com.mkz.api.dto.promotion.OrderCourseDTO;
import com.mkz.common.exceptions.BizIllegalException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

import java.util.Collections;
import java.util.List;

@Slf4j
public class PromotionClientFallback implements FallbackFactory<PromotionClient> {

    @Override
    public PromotionClient create(Throwable cause) {
        log.error("查询促销服务异常", cause);
        return new PromotionClient() {
            @Override
            public List<CouponDiscountDTO> findDiscountSolution(List<OrderCourseDTO> courses) {
                return null;
            }

            @Override
            public CouponDetailSimpleVO querySimpleCouponById(Long id) {
                return null;
            }

            @Override
            public List<Long> transformCouponIds(List<Long> couponIds) {
                return null;
            }

            @Override
            public CouponDiscountDTO queryDiscountDetailByOrder(OrderCouponDTO orderCouponDTO) {
                return null;
            }

            @Override
            public void writeOffCoupon(List<Long> userCouponIds) {
                throw new BizIllegalException(500, "核销优惠券异常", cause);
            }

            @Override
            public void refundCoupon(List<Long> userCouponIds) {
                throw new BizIllegalException(500, "退还优惠券异常", cause);
            }

            @Override
            public List<String> queryDiscountRules(List<Long> userCouponIds) {
                return Collections.emptyList();
            }
        };
    }
}
