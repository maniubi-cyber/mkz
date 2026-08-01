package com.mkz.promotion.mapper;

import com.mkz.promotion.domain.po.Coupon;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * <p>
 * 优惠券的规则信息 Mapper 接口
 * </p>
 *
 * 支持乐观锁并发控制：
 * - 通过版本号字段实现乐观锁
 * - 配合Redis Lua脚本作为前置校验
 * - 双重保障优惠券发放的一致性
 *
 * @author fsq
 * @since 2023-10-28
 */
public interface CouponMapper extends BaseMapper<Coupon> {

    /**
     * 更新优惠券已领取数（原子操作，内置乐观锁校验）
     * 通过SQL条件判断确保不超发
     *
     * @param id 优惠券ID
     * @return 影响行数，0表示库存不足或并发冲突
     */
    @Update("update coupon set issue_num = issue_num + 1 where id = #{id} and issue_num < total_num")
    int incrIssueNum(@Param("id") Long id);

    /**
     * 使用乐观锁更新优惠券发放数量
     * 通过版本号确保并发安全
     *
     * @param id      优惠券ID
     * @param version 当前版本号
     * @return 影响行数，0表示版本号不匹配（并发冲突）
     */
    @Update("update coupon set issue_num = issue_num + 1, version = version + 1 " +
            "where id = #{id} and issue_num < total_num and version = #{version}")
    int incrIssueNumWithVersion(@Param("id") Long id, @Param("version") Integer version);

    /**
     * 使用乐观锁核销优惠券
     *
     * @param id      优惠券ID
     * @param version 当前版本号
     * @return 影响行数
     */
    @Update("update coupon set used_num = used_num + 1, version = version + 1 " +
            "where id = #{id} and version = #{version}")
    int incrUsedNumWithVersion(@Param("id") Long id, @Param("version") Integer version);

    /**
     * 扣减库存（使用乐观锁）
     *
     * @param id      优惠券ID
     * @param version 当前版本号
     * @return 影响行数
     */
    @Update("update coupon set total_num = total_num - 1, version = version + 1 " +
            "where id = #{id} and total_num > 0 and version = #{version}")
    int decrTotalNumWithVersion(@Param("id") Long id, @Param("version") Integer version);
}
