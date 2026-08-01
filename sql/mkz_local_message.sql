-- 本地消息表
-- 用于实现消息的最终一致性
CREATE TABLE IF NOT EXISTS `local_message` (
    `id` BIGINT NOT NULL COMMENT '消息ID',
    `topic` VARCHAR(100) NOT NULL COMMENT '消息主题',
    `tags` VARCHAR(100) DEFAULT NULL COMMENT '消息标签',
    `content` TEXT COMMENT '消息内容（JSON格式）',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '消息状态：0-待发送，1-发送中，2-发送成功，3-发送失败',
    `retry_count` INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    `max_retry_count` INT NOT NULL DEFAULT 3 COMMENT '最大重试次数',
    `next_retry_time` DATETIME DEFAULT NULL COMMENT '下次重试时间',
    `error_msg` VARCHAR(500) DEFAULT NULL COMMENT '错误信息',
    `business_id` VARCHAR(100) DEFAULT NULL COMMENT '关联业务ID（用于幂等性校验）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status_retry` (`status`, `next_retry_time`),
    KEY `idx_business_id` (`business_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地消息表';
