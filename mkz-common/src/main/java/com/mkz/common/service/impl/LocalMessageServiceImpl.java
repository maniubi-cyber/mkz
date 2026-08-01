package com.mkz.common.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mkz.common.autoconfigure.mq.RocketMqHelper;
import com.mkz.common.domain.po.LocalMessage;
import com.mkz.common.mapper.LocalMessageMapper;
import com.mkz.common.service.LocalMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 本地消息服务实现类
 * 
 * 实现本地消息表 + XXL-Job定时扫描补偿机制：
 * 1. 业务操作时保存消息记录（本地事务）
 * 2. 异步发送消息到RocketMQ
 * 3. 更新消息发送状态
 * 4. 定时任务扫描失败消息进行补偿重试
 * 
 * 确保消息在异常情况下的最终一致性
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LocalMessageServiceImpl implements LocalMessageService {

    private final LocalMessageMapper localMessageMapper;
    private final RocketMqHelper rocketMqHelper;
    private final ObjectMapper objectMapper;

    /**
     * 默认最大重试次数
     */
    private static final int DEFAULT_MAX_RETRY = 3;

    /**
     * 默认重试间隔（分钟）
     */
    private static final int DEFAULT_RETRY_INTERVAL = 5;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public LocalMessage saveMessage(String topic, String tags, String content, String businessId) {
        LocalMessage message = new LocalMessage();
        message.setTopic(topic);
        message.setTags(tags);
        message.setContent(content);
        message.setBusinessId(businessId);
        message.setStatus(LocalMessage.Status.PENDING);
        message.setRetryCount(0);
        message.setMaxRetryCount(DEFAULT_MAX_RETRY);
        message.setNextRetryTime(LocalDateTime.now());
        message.setCreateTime(LocalDateTime.now());
        message.setUpdateTime(LocalDateTime.now());

        localMessageMapper.insert(message);
        log.info("保存本地消息记录，id: {}, topic: {}, businessId: {}", message.getId(), topic, businessId);
        return message;
    }

    @Override
    public boolean sendMessage(LocalMessage message) {
        try {
            // 更新状态为发送中（同时 retry_count + 1，next_retry_time 顺延）
            int rows = localMessageMapper.updateStatusToSending(message.getId());
            if (rows == 0) {
                log.warn("消息状态不是待发送，可能已被其他线程处理，id: {}", message.getId());
                return false;
            }

            // 同步发送消息到RocketMQ（补偿任务需精确感知结果）
            boolean success = rocketMqHelper.sendSync(message.getTopic(), message.getTags(), message.getContent());

            if (success) {
                markSuccess(message.getId());
                log.info("消息发送成功，id: {}", message.getId());
                return true;
            }

            // 发送失败：判断是否已耗尽重试次数
            int newRetryCount = message.getRetryCount() + 1;
            if (newRetryCount >= message.getMaxRetryCount()) {
                // 进入死信：停止自动补偿，发出告警（ERROR 日志便于监控采集）
                String deadMsg = String.format("重试次数耗尽(%d/%d)，进入死信状态",
                        newRetryCount, message.getMaxRetryCount());
                localMessageMapper.updateStatusToDead(message.getId(), deadMsg);
                log.error("[死信告警] 本地消息不再自动补偿，需人工介入。id={}, topic={}, tags={}, businessId={}, {}",
                        message.getId(), message.getTopic(), message.getTags(), message.getBusinessId(), deadMsg);
            } else {
                markFailed(message.getId(), "RocketMQ返回发送失败");
                log.warn("消息发送失败，id: {}，已重试 {} 次，等待下次补偿", message.getId(), newRetryCount);
            }
            return false;
        } catch (Exception e) {
            log.error("消息发送异常，id: {}", message.getId(), e);
            markFailed(message.getId(), e.getMessage());
            return false;
        }
    }

    @Override
    public void markSuccess(Long messageId) {
        localMessageMapper.updateStatusToSuccess(messageId);
    }

    @Override
    public void markFailed(Long messageId, String errorMsg) {
        // 截断错误信息，防止过长
        if (errorMsg != null && errorMsg.length() > 500) {
            errorMsg = errorMsg.substring(0, 500);
        }
        localMessageMapper.updateStatusToFailed(messageId, errorMsg);
    }

    @Override
    public List<LocalMessage> getPendingMessages(int limit) {
        return localMessageMapper.selectPendingMessages(LocalDateTime.now(), limit);
    }

    @Override
    public boolean compensateMessage(LocalMessage message) {
        log.info("开始补偿发送消息，id: {}, retryCount: {}", message.getId(), message.getRetryCount());
        return sendMessage(message);
    }

    @Override
    public int cleanExpiredMessages(LocalDateTime expireTime) {
        return localMessageMapper.deleteByStatusAndTime(LocalMessage.Status.SUCCESS, expireTime);
    }

    /**
     * 发送业务消息（组合方法）
     * 在业务事务中保存本地消息记录，并在【业务事务提交后】异步发送到 RocketMQ。
     * <p>
     * 关键修正：MQ 投递通过 {@code TransactionSynchronizationManager#afterCommit} 延迟到事务提交之后，
     * 避免消费端在事务未提交时读到尚未生效的业务数据（本地消息表标准实践）。
     * 若事务回滚，afterCommit 不会触发，消息不发送；记录仍处于待发送状态，由 XXL-Job 补偿任务重试，
     * 从而保障消息最终一致性。
     * <p>
     * businessId 同时作为本地消息表的幂等键与 RocketMQ 消息的 keys，
     * 消费端可通过 message.getKeys() 获取后用于消费幂等。
     *
     * @param topic      主题
     * @param tags       标签
     * @param content    消息内容
     * @param businessId 业务ID（幂等键，写入 RocketMQ 消息 keys）
     * @return 是否处理成功（已发送过返回 true）
     */
    @Override
    @Transactional
    public boolean sendBusinessMessage(String topic, String tags, Object content, String businessId) {
        try {
            // 1. 幂等校验：同一 businessId 已成功发送则直接返回
            LocalMessage existingMessage = localMessageMapper.selectByBusinessId(businessId);
            if (existingMessage != null) {
                log.info("消息已发送，无需重复发送，businessId: {}", businessId);
                return true;
            }

            // 2. 序列化消息内容
            String contentJson = objectMapper.writeValueAsString(content);

            // 3. 保存消息记录（与业务同事务：业务回滚则消息不会落库，也不会发送）
            LocalMessage message = saveMessage(topic, tags, contentJson, businessId);

            // 4. 注册事务同步：在业务事务【提交后】才真正投递 MQ。
            //    若事务回滚，afterCommit 不会触发，消息不发送，由补偿任务按待发送状态重试。
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                dispatchAsync(message, topic, tags, contentJson, businessId);
                            }
                        });
            } else {
                // 极端兜底：无事务上下文时立即投递（正常情况下不会走到这里）
                dispatchAsync(message, topic, tags, contentJson, businessId);
            }
            return true;
        } catch (JsonProcessingException e) {
            log.error("消息序列化失败", e);
            return false;
        }
    }

    /**
     * 真正投递 MQ（在事务提交后调用）。
     * 异步发送，回调中更新本地消息表状态；发送失败由 XXL-Job 补偿任务重试。
     */
    private void dispatchAsync(LocalMessage message, String topic, String tags,
                               String contentJson, String businessId) {
        org.springframework.messaging.Message<String> mqMessage = org.springframework.messaging.support.MessageBuilder
                .withPayload(contentJson)
                .setHeader(org.apache.rocketmq.spring.support.RocketMQHeaders.KEYS, businessId)
                .build();

        rocketMqHelper.sendAsync(topic, tags, mqMessage, new RocketMqHelper.SendCallback() {
            @Override
            public void onSuccess(org.apache.rocketmq.client.producer.SendResult sendResult) {
                markSuccess(message.getId());
            }

            @Override
            public void onException(Throwable e) {
                markFailed(message.getId(), e.getMessage());
            }
        });
    }
}
