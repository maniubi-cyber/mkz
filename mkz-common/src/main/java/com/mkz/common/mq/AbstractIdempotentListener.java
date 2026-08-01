package com.mkz.common.mq;

import com.mkz.common.utils.MessageIdempotentUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 幂等消费基类
 * 子类实现 doConsume 方法，基类负责幂等校验，
 * 确保同一条消息即使被重复投递也只被业务处理一次。
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractIdempotentListener<T> {

    private final MessageIdempotentUtil idempotentUtil;

    /**
     * 消费入口：先做幂等校验，再交给子类处理
     *
     * @param businessId 业务唯一标识（幂等键，通常取消息中的 businessId）
     * @param message    消息体
     */
    protected void consume(String businessId, T message) {
        if (!idempotentUtil.checkAndMark(businessId)) {
            log.info("[幂等消费] 消息已消费过，跳过: businessId={}", businessId);
            return;
        }
        try {
            doConsume(message);
        } catch (Exception e) {
            log.error("[幂等消费] 处理失败: businessId={}", businessId, e);
            throw e;
        }
    }

    /**
     * 子类实现的实际业务处理逻辑
     *
     * @param message 消息体
     */
    protected abstract void doConsume(T message);
}
