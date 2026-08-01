package com.mkz.promotion.handler;

import com.mkz.api.dto.data.TodoDataVO;
import com.mkz.common.autoconfigure.mq.RabbitMqHelper;
import com.mkz.common.constants.MqConstants;
import com.mkz.promotion.domain.po.Coupon;
import com.mkz.promotion.enums.CouponStatus;
import com.mkz.promotion.service.ICouponService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @Author: fsq
 * @Date: 2025/5/25 10:03
 * @Version: 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponDataJobHandler {
    private final ICouponService couponService;
    private final RabbitMqHelper mqHelper;

    @XxlJob("couponDataJobHandler")
    public void handleCouponTodoData() {
        Integer count = couponService.lambdaQuery()
                .eq(Coupon::getStatus, CouponStatus.DRAFT).count();
        TodoDataVO todoDataVO = new TodoDataVO();
        todoDataVO.setTodoCouponNum(count);
        //发送给数据微服务
        mqHelper.send(MqConstants.Exchange.DATA_EXCHANGE, MqConstants.Key.DATA_TODO_KEY, todoDataVO);
    }

}
