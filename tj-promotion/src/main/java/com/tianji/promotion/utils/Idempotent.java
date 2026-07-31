package com.tianji.promotion.utils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 幂等性校验注解
 * 
 * 基于Redisson + AOP实现零侵入式防重控制：
 * - 通过Redisson分布式锁确保同一操作不会被重复执行
 * - 支持SpEL表达式动态生成锁key
 * - 支持自定义等待时间和锁释放时间
 * 
 * 使用场景：
 * - 优惠券领取防重
 * - 点赞操作防重
 * - 订单提交防重等
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Idempotent {

    /**
     * 锁名称，支持SpEL表达式
     * 例如："coupon:receive:#{userId}:#{couponId}"
     */
    String lockName();

    /**
     * 获取锁的等待时间
     * 默认1秒
     */
    long waitTime() default 1L;

    /**
     * 锁的持有时间（-1表示使用看门狗机制自动续期）
     * 默认-1
     */
    long leaseTime() default -1L;

    /**
     * 时间单位
     * 默认秒
     */
    TimeUnit unit() default TimeUnit.SECONDS;

    /**
     * 锁类型
     * 默认可重入锁
     */
    MyLockType lockType() default MyLockType.RE_ENTRANT_LOCK;

    /**
     * 获取锁失败策略
     * 默认重试超时后抛出异常
     */
    MyLockStrategy lockStrategy() default MyLockStrategy.FAIL_AFTER_RETRY_TIMEOUT;

    /**
     * 幂等key前缀
     * 用于Redis中存储幂等标记
     */
    String idempotentPrefix() default "idempotent:";

    /**
     * 幂等标记过期时间（秒）
     * 默认24小时
     */
    long idempotentExpire() default 86400L;

    /**
     * 是否启用数据库乐观锁兜底
     * 默认启用
     */
    boolean enableOptimisticLockFallback() default true;
}
