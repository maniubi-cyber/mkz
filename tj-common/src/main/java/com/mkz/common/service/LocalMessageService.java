package com.mkz.common.service;

import com.mkz.common.domain.po.LocalMessage;

import java.util.List;

/**
 * 本地消息服务接口
 * 
 * 实现消息的最终一致性：
 * - 业务操作时同时保存消息记录（本地事务）
 * - 发送消息到RocketMQ
 * - 定时扫描补偿发送失败的消息
 */
public interface LocalMessageService {

    /**
     * 保存消息记录（在业务事务中调用）
     *
     * @param topic      主题
     * @param tags       标签
     * @param content    消息内容
     * @param businessId 业务ID
     * @return 消息记录
     */
    LocalMessage saveMessage(String topic, String tags, String content, String businessId);

    /**
     * 发送消息并更新状态
     *
     * @param message 消息记录
     * @return 是否发送成功
     */
    boolean sendMessage(LocalMessage message);

    /**
     * 标记消息发送成功
     *
     * @param messageId 消息ID
     */
    void markSuccess(Long messageId);

    /**
     * 标记消息发送失败
     *
     * @param messageId 消息ID
     * @param errorMsg  错误信息
     */
    void markFailed(Long messageId, String errorMsg);

    /**
     * 查询待补偿的消息
     *
     * @param limit 限制数量
     * @return 消息列表
     */
    List<LocalMessage> getPendingMessages(int limit);

    /**
     * 补偿发送消息
     *
     * @param message 消息记录
     * @return 是否发送成功
     */
    boolean compensateMessage(LocalMessage message);

    /**
     * 清理过期消息
     *
     * @param expireTime 过期时间（在此时间之前且已发送成功的消息将被清理）
     * @return 清理条数
     */
    int cleanExpiredMessages(java.time.LocalDateTime expireTime);

    /**
     * 发送业务消息（组合方法）
     * <p>
     * 在业务事务中保存本地消息记录，并异步发送到 RocketMQ。发送失败由 XXL-Job 补偿任务重试，
     * 保障消息的最终一致性。
     * <p>
     * businessId 同时作为本地消息表的幂等键与 RocketMQ 消息的 keys，
     * 消费端可通过 message.getKeys() 获取后用于消费幂等。
     *
     * @param topic      主题
     * @param tags       标签（可用于消费端区分业务操作，如 up/down）
     * @param content    消息内容（会被 JSON 序列化）
     * @param businessId 业务ID（幂等键，写入 RocketMQ 消息 keys）
     * @return 是否处理成功（已发送过返回 true）
     */
    boolean sendBusinessMessage(String topic, String tags, Object content, String businessId);
}
