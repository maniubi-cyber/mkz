package com.mkz.search.service;

import com.mkz.api.dto.trade.OrderAnalysisDTO;
import com.mkz.search.domain.po.Order;

import java.util.List;

/**
 * @author fsq
 * @date 2025/5/25 9:48
 */
public interface IOrderService {

    void saveAll(List<Order> orders);
}
