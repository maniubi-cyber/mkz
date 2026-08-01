package com.mkz.promotion.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mkz.promotion.domain.dto.CouponDiscountDTO;
import com.mkz.promotion.domain.dto.OrderCouponDTO;

import java.util.List;

public interface IDiscountService {

    CouponDiscountDTO queryDiscountDetailByOrder(OrderCouponDTO orderCouponDTO);
}