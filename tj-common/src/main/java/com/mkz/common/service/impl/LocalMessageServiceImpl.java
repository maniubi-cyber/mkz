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
            // 更新状态为发送中
            int rows = localMessageMapper.updateStatusToSending(message.getId());
            if (rows == 0) {
                log.warn("消息状态不是待发送，可能已被其他线程处理，id: {}", message.getId());
                return false;
            }

            // 发送消息到RocketMQ
            boolean success = rocketMqHelper.sendSync(message.getTopic(), message.getTags(), message.getContent());

            if (success) {
                // 标记发送成功
                markSuccess(message.getId());
                log.info("消息发送成功，id: {}", message.getId());
            } else {
                // 标记发送失败
                markFailed(message.getId(), "RocketMQ返回发送失败");
                log.warn("消息发送失败，id: {}", message.getId());
            }

            return success;
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
     * 在业务事务中保存本地消息记录，并异步发送到 RocketMQ。
     * 发送失败由 XXL-Job 补偿任务重试，保障消息最终一致性。
     *
     * @param topic      主题
     * @param tags       标签
     * @param content    消息内容
     * @param businessId 业务ID（幂等键，同时写入RocketMQ消息keys）
     * @return 是否处理成功（已发送过返回true）
     */
    @Override
    @Transactional
    public boolean sendBusinessMessage(String topic, String tags, Object content, String businessId) {
        try {
            // 1. 检查幂等性（根据业务ID查询是否已发送成功）
            LocalMessage existingMessage = localMessageMapper.selectByBusinessId(businessId);
            if (existingMessage != null) {
                log.info("消息已发送，无需重复发送，businessId: {}", businessId);
                return true;
            }

            // 2. 序列化消息内容
            String contentJson = objectMapper.writeValueAsString(content);

            // 3. 保存消息记录（与业务同事务，业务回滚则消息不会发送）
            LocalMessage message = saveMessage(topic, tags, contentJson, businessId);

            // 4. 构造带 businessId(keys) 的消息，便于消费端通过 message.getKeys() 做消费幂等
            org.springframework.messaging.Message<String> mqMessage = org.springframework.messaging.support.MessageBuilder
                    .withPayload(contentJson)
                    .setHeader(org.apache.rocketmq.spring.support.RocketMQHeaders.KEYS, businessId)
                    .build();

            // 5. 异步发送消息，回调中更新本地消息表状态（失败由补偿任务重试）
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

            return true;
        } catch (JsonProcessingException e) {
            log.error("消息序列化失败", e);
            return false;
        }
    }
}
