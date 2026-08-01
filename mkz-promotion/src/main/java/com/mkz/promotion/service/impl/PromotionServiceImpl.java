package com.mkz.promotion.service.impl;

import com.mkz.promotion.domain.po.Promotion;
import com.mkz.promotion.mapper.PromotionMapper;
import com.mkz.promotion.service.IPromotionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 促销活动，形式多种多样，例如：优惠券 服务实现类
 * </p>
 *
 * @author fsq
 * @since 2023-10-28
 */
@Service
public class PromotionServiceImpl extends ServiceImpl<PromotionMapper, Promotion> implements IPromotionService {

}
