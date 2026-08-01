package com.mkz.common.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 本地消息表实体
 * 
 * 用于实现消息的最终一致性：
 * - 业务操作和消息记录在同一个本地事务中保存
 * - 定时任务扫描未发送成功的消息进行补偿重试
 * - 确保消息不会因为系统异常而丢失
 */
@Data
@TableName("local_message")
public class LocalMessage {

    /**
     * 消息ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 消息主题
     */
    private String topic;

    /**
     * 消息标签
     */
    private String tags;

    /**
     * 消息内容（JSON格式）
     */
    private String content;

    /**
     * 消息状态：0-待发送，1-发送中，2-发送成功，3-发送失败
     */
    private Integer status;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 最大重试次数
     */
    private Integer maxRetryCount;

    /**
     * 下次重试时间
     */
    private LocalDateTime nextRetryTime;

    /**
     * 错误信息
     */
    private String errorMsg;

    /**
     * 关联业务ID（用于幂等性校验）
     */
    private String businessId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 消息状态枚举
     */
    public static class Status {
        /** 待发送 */
        public static final int PENDING = 0;
        /** 发送中 */
        public static final int SENDING = 1;
        /** 发送成功 */
        public static final int SUCCESS = 2;
        /** 发送失败 */
        public static final int FAILED = 3;
        /** 死信：重试次数已耗尽，停止自动补偿，需人工介入/告警 */
        public static final int DEAD = 4;
    }
}
