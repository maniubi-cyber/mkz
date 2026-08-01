package com.mkz.search.mq;/**
 * @author fsq
 * @date 2025/5/25 10:39
 */

import com.mkz.api.dto.trade.OrderAnalysisDTO;
import com.mkz.api.dto.trade.OrderDetailAnalysisDTO;
import com.mkz.common.utils.BeanUtils;
import com.mkz.search.domain.po.Order;
import com.mkz.search.domain.po.OrderDetail;
import com.mkz.search.service.ICourseService;
import com.mkz.search.service.IOrderDetailService;
import com.mkz.search.service.IOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.mkz.common.constants.MqConstants.Exchange.ORDER_EXCHANGE;
import static com.mkz.common.constants.MqConstants.Key.ORDER_ANALYSIS_KEY;
import static com.mkz.common.constants.MqConstants.Key.ORDER_DETAIL_ANALYSIS_KEY;

/**
 * @Author: fsq
 * @Date: 2025/5/25 10:39
 * @Version: 1.0
 */
@Slf4j
@Component
public class OrderAnalysisListener {

    @Autowired
    private IOrderService orderService;
    @Autowired
    private IOrderDetailService orderDetailService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "search.order.anslysis.queue", durable = "true"),
            exchange = @Exchange(name = ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = ORDER_ANALYSIS_KEY
    ))
    public void listenOrderAnalysis(List<OrderAnalysisDTO> dtoList){
        log.info("OrderAnalysisListener接收到了消息：{}",dtoList);
        List<Order> orders = new ArrayList<>();
        for (OrderAnalysisDTO orderAnalysisDTO : dtoList) {
            Order order = BeanUtils.copyProperties(orderAnalysisDTO, Order.class);
            orders.add(order);
        }
        orderService.saveAll(orders);
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "search.order.detail.anslysis.queue", durable = "true"),
            exchange = @Exchange(name = ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = ORDER_DETAIL_ANALYSIS_KEY
    ))
    public void listenOrderDetailAnalysis(List<OrderDetailAnalysisDTO> dtoList){
        log.info("OrderAnalysisListener接收到了消息：{}",dtoList);
        List<OrderDetail> orderDetails = new ArrayList<>();
        for (OrderDetailAnalysisDTO orderDetailAnalysisDTO : dtoList) {
            OrderDetail orderDetail =  BeanUtils.copyProperties(orderDetailAnalysisDTO, OrderDetail.class);
            orderDetails.add(orderDetail);
        }
        orderDetailService.saveAll(orderDetails);
    }



}
