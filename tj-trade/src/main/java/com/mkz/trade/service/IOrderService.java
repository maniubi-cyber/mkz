package com.mkz.trade.service;

import com.mkz.common.domain.dto.PageDTO;
import com.mkz.pay.sdk.dto.PayResultDTO;
import com.mkz.trade.constants.OrderCancelReason;
import com.mkz.trade.domain.dto.PlaceOrderDTO;
import com.mkz.trade.domain.po.Order;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mkz.trade.domain.po.OrderDetail;
import com.mkz.trade.domain.query.OrderPageQuery;
import com.mkz.trade.domain.vo.OrderConfirmVO;
import com.mkz.trade.domain.vo.OrderPageVO;
import com.mkz.trade.domain.vo.OrderVO;
import com.mkz.trade.domain.vo.PlaceOrderResultVO;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 订单 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-08-29
 */
public interface IOrderService extends IService<Order> {

    PlaceOrderResultVO placeOrder(PlaceOrderDTO placeOrderDTO);

    @Transactional
    void saveOrderAndDetails(Order order, List<OrderDetail> orderDetails);

    void cancelOrder(Long orderId, OrderCancelReason cancelReason);

    void deleteOrder(Long id);

    PageDTO<OrderPageVO> queryMyOrderPage(OrderPageQuery pageQuery);

    OrderVO queryOrderById(Long id);

    PlaceOrderResultVO queryOrderStatus(Long orderId);

    List<Order> queryOrderBetweenTime(LocalDateTime date1, LocalDateTime date2);

    void handlePaySuccess(PayResultDTO payResult);

    PlaceOrderResultVO enrolledFreeCourse(Long courseId);

    OrderConfirmVO prePlaceOrder(List<Long> courseIds);

}
