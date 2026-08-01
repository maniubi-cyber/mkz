package com.mkz.promotion.service;

import com.mkz.api.dto.promotion.CouponDetailSimpleVO;
import com.mkz.common.domain.dto.PageDTO;
import com.mkz.promotion.domain.dto.CouponFormDTO;
import com.mkz.promotion.domain.dto.CouponIssueFormDTO;
import com.mkz.promotion.domain.po.Coupon;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mkz.promotion.domain.query.CouponQuery;
import com.mkz.promotion.domain.vo.CouponDetailVO;
import com.mkz.promotion.domain.vo.CouponPageVO;
import com.mkz.promotion.domain.vo.CouponVO;

import java.util.List;

/**
 * <p>
 * 优惠券的规则信息 服务类
 * </p>
 *
 * @author fsq
 * @since 2023-10-28
 */
public interface ICouponService extends IService<Coupon> {

    void saveCoupon(CouponFormDTO dto);

    PageDTO<CouponPageVO> queryCouponPage(CouponQuery query);

    void issueCoupon(Long id,CouponIssueFormDTO dto);

    void modifyCoupon(Long id,CouponFormDTO dto);

    CouponDetailVO queryCouponById(Long id);

    void pauseIssue(Long id);

    void deleteById(Long id);

    void beginIssueBatch(List<Coupon> records);

    List<CouponVO> queryIssuingCoupons();

    CouponDetailSimpleVO querySimpleCouponById(Long id);
}
