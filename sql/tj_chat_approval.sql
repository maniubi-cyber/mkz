-- AI审批记录表
CREATE TABLE IF NOT EXISTS `chat_approval_record` (
    `approval_id` VARCHAR(50) NOT NULL COMMENT '审批ID',
    `session_id` VARCHAR(100) DEFAULT NULL COMMENT '会话ID',
    `tool_name` VARCHAR(100) NOT NULL COMMENT '工具名称',
    `operation_summary` VARCHAR(500) DEFAULT NULL COMMENT '操作摘要',
    `parameters` TEXT COMMENT '操作参数（JSON格式）',
    `approval_status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '审批状态：PENDING-待审批，APPROVED-已批准，REJECTED-已拒绝',
    `approver_id` BIGINT DEFAULT NULL COMMENT '审批人ID',
    `approval_comment` VARCHAR(500) DEFAULT NULL COMMENT '审批意见',
    `user_id` BIGINT DEFAULT NULL COMMENT '发起操作的用户ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `approved_at` DATETIME DEFAULT NULL COMMENT '审批时间',
    PRIMARY KEY (`approval_id`),
    KEY `idx_status` (`approval_status`),
    KEY `idx_tool_name` (`tool_name`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI审批记录表';
