package com.tianji.chat.tools;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 工具执行结果
 * 
 * 工具调用返回结构化JSON数据，解析失败时自动携带格式纠正提示词重试；
 * 工具执行异常（如接口超时）不中断循环，而是将错误信息作为结果回传，
 * 由LLM自行决定降级回复或切换工具。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecutionResult {

    /**
     * 是否执行成功
     */
    private boolean success;

    /**
     * 结果数据（JSON格式）
     */
    private String data;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 是否需要人工审批（高风险工具）
     */
    private boolean requiresApproval;

    /**
     * 审批ID（需要审批时）
     */
    private String approvalId;

    /**
     * 审批状态
     */
    private ApprovalStatus approvalStatus;

    /**
     * 执行时间
     */
    private LocalDateTime executionTime;

    /**
     * 执行耗时（毫秒）
     */
    private Long executionDuration;

    /**
     * 工具名称
     */
    private String toolName;

    /**
     * 审批状态枚举
     */
    public enum ApprovalStatus {
        PENDING("待审批"),
        APPROVED("已批准"),
        REJECTED("已拒绝"),
        NOT_REQUIRED("无需审批");

        private final String description;

        ApprovalStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 创建成功结果
     */
    public static ToolExecutionResult success(String toolName, String data) {
        return ToolExecutionResult.builder()
                .success(true)
                .data(data)
                .toolName(toolName)
                .requiresApproval(false)
                .approvalStatus(ApprovalStatus.NOT_REQUIRED)
                .executionTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建需要审批的结果
     */
    public static ToolExecutionResult requiresApproval(String toolName, String operationSummary) {
        return ToolExecutionResult.builder()
                .success(false)
                .toolName(toolName)
                .requiresApproval(true)
                .approvalId(generateApprovalId())
                .approvalStatus(ApprovalStatus.PENDING)
                .data(operationSummary)
                .executionTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建失败结果
     */
    public static ToolExecutionResult failure(String toolName, String errorMessage) {
        return ToolExecutionResult.builder()
                .success(false)
                .toolName(toolName)
                .errorMessage(errorMessage)
                .requiresApproval(false)
                .approvalStatus(ApprovalStatus.NOT_REQUIRED)
                .executionTime(LocalDateTime.now())
                .build();
    }

    private static String generateApprovalId() {
        return "APV-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 10000);
    }
}
