package com.tianji.chat.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tianji.chat.tools.ToolExecutionResult;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批记录实体
 * 
 * 存储人工审批的相关信息
 */
@Data
@TableName("chat_approval_record")
public class ApprovalRecord {

    /**
     * 审批ID
     */
    @TableId(type = IdType.INPUT)
    private String approvalId;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 工具名称
     */
    private String toolName;

    /**
     * 操作摘要
     */
    private String operationSummary;

    /**
     * 操作参数（JSON格式）
     */
    private String parameters;

    /**
     * 审批状态
     */
    private String approvalStatus;

    /**
     * 审批人ID
     */
    private Long approverId;

    /**
     * 审批意见
     */
    private String approvalComment;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 审批时间
     */
    private LocalDateTime approvedAt;

    /**
     * 用户ID（发起操作的用户）
     */
    private Long userId;

    public ToolExecutionResult.ApprovalStatus getApprovalStatusEnum() {
        return ToolExecutionResult.ApprovalStatus.valueOf(approvalStatus);
    }

    public void setApprovalStatusEnum(ToolExecutionResult.ApprovalStatus status) {
        this.approvalStatus = status.name();
    }
}
