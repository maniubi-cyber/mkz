package com.tianji.common.autoconfigure.mq;

import cn.hutool.core.lang.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.support.MessageBuilder;

/**
 * RocketMQ消息发送助手
 * 
 * 基于RocketMQ实现消息异步解耦：
 * - 支持同步发送、异步发送
 * - 支持事务消息
 * - 支持延迟消息
 * 
 * 配合本地消息表 + XXL-Job定时扫描补偿机制，
 * 确保消息在异常情况下的最终一致性
 */
@Slf4j
public class RocketMqHelper {

    private final RocketMQTemplate rocketMQTemplate;

    @Value("${rocketmq.producer.send-message-timeout:3000}")
    private int sendTimeout;

    public RocketMqHelper(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    /**
     * 同步发送消息
     *
     * @param topic 主题
     * @param msg   消息内容
     * @param <T>   消息类型
     * @return 发送结果
     */
    public <T> boolean sendSync(String topic, T msg) {
        return sendSync(topic, null, msg);
    }

    /**
     * 同步发送消息（带标签）
     *
     * @param topic 主题
     * @param tags  标签
     * @param msg   消息内容
     * @param <T>   消息类型
     * @return 发送结果
     */
    public <T> boolean sendSync(String topic, String tags, T msg) {
        try {
            String destination = tags != null ? topic + ":" + tags : topic;
            SendResult result = rocketMQTemplate.syncSend(destination, msg);
            boolean success = SendStatus.SEND_OK.equals(result.getSendStatus());
            if (success) {
                log.info("消息发送成功，topic={}, tags={}, msgId={}", topic, tags, result.getMsgId());
            } else {
                log.warn("消息发送状态非OK，topic={}, tags={}, sendStatus={}", 
                        topic, tags, result.getSendStatus());
            }
            return success;
        } catch (Exception e) {
            log.error("消息发送失败，topic={}, tags={}", topic, tags, e);
            return false;
        }
    }

    /**
     * 异步发送消息
     *
     * @param topic    主题
     * @param msg      消息内容
     * @param callback 回调函数
     * @param <T>      消息类型
     */
    public <T> void sendAsync(String topic, T msg, SendCallback callback) {
        sendAsync(topic, null, msg, callback);
    }

    /**
     * 异步发送消息（带标签）
     *
     * @param topic    主题
     * @param tags     标签
     * @param msg      消息内容
     * @param callback 回调函数
     * @param <T>      消息类型
     */
    public <T> void sendAsync(String topic, String tags, T msg, SendCallback callback) {
        String destination = tags != null ? topic + ":" + tags : topic;
        rocketMQTemplate.asyncSend(destination, msg, new org.apache.rocketmq.client.producer.SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("异步消息发送成功，topic={}, tags={}, msgId={}", topic, tags, sendResult.getMsgId());
                if (callback != null) {
                    callback.onSuccess(sendResult);
                }
            }

            @Override
            public void onException(Throwable e) {
                log.error("异步消息发送失败，topic={}, tags={}", topic, tags, e);
                if (callback != null) {
                    callback.onException(e);
                }
            }
        });
    }

    /**
     * 发送事务消息
     *
     * @param topic 主题
     * @param tags  标签
     * @param msg   消息内容
     * @param <T>   消息类型
     * @return 发送结果
     */
    public <T> boolean sendTransaction(String topic, String tags, T msg) {
        try {
            String destination = tags != null ? topic + ":" + tags : topic;
            SendResult result = rocketMQTemplate.sendMessageInTransaction(
                    destination,
                    MessageBuilder.withPayload(msg).setHeader("msgId", UUID.randomUUID().toString()).build(),
                    null
            );
            boolean success = result != null && SendStatus.SEND_OK.equals(result.getSendStatus());
            if (success) {
                log.info("事务消息发送成功，destination={}, msgId={}", destination, result.getMsgId());
            }
            return success;
        } catch (Exception e) {
            log.error("事务消息发送失败，topic={}, tags={}", topic, tags, e);
            return false;
        }
    }

    /**
     * 发送延迟消息
     * 
     * 延迟级别：
     * 1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
     *
     * @param topic      主题
     * @param tags       标签
     * @param msg        消息内容
     * @param delayLevel 延迟级别（1-18）
     * @param <T>        消息类型
     * @return 发送结果
     */
    public <T> boolean sendDelay(String topic, String tags, T msg, int delayLevel) {
        if (delayLevel <= 0 || delayLevel > 18) {
            log.error("延迟级别必须在1-18之间，当前值: {}", delayLevel);
            return false;
        }

        try {
            String destination = tags != null ? topic + ":" + tags : topic;
            SendResult result = rocketMQTemplate.syncSend(
                    destination,
                    MessageBuilder.withPayload(msg).build(),
                    sendTimeout,
                    delayLevel
            );
            boolean success = result != null && SendStatus.SEND_OK.equals(result.getSendStatus());
            if (success) {
                log.info("延迟消息发送成功，topic={}, delayLevel={}, msgId={}", topic, delayLevel, result.getMsgId());
            }
            return success;
        } catch (Exception e) {
            log.error("延迟消息发送失败，topic={}, delayLevel={}", topic, delayLevel, e);
            return false;
        }
    }

    /**
     * 发送回调接口
     */
    public interface SendCallback {
        void onSuccess(SendResult sendResult);
        void onException(Throwable e);
    }
}
