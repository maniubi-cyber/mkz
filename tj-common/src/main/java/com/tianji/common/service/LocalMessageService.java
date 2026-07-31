package com.tianji.common.service;

import com.tianji.common.domain.po.LocalMessage;

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
}
