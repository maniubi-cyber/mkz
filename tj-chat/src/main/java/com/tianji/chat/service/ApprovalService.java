package com.tianji.chat.service;

import com.tianji.chat.tools.ToolExecutionResult;

/**
 * 人工审批服务接口
 * 
 * 高级风险工具触发Human-in-the-Loop审批：
 * - Agent暂停并推送操作摘要至管理端
 * - 人工确认后方可继续
 */
public interface ApprovalService {

    /**
     * 创建审批请求
     *
     * @param toolName        工具名称
     * @param operationSummary 操作摘要
     * @param parameters      操作参数
     * @return 审批ID
     */
    String createApprovalRequest(String toolName, String operationSummary, String parameters);

    /**
     * 查询审批状态
     *
     * @param approvalId 审批ID
     * @return 审批状态
     */
    ToolExecutionResult.ApprovalStatus getApprovalStatus(String approvalId);

    /**
     * 审批通过
     *
     * @param approvalId 审批ID
     * @param approverId 审批人ID
     * @return 是否成功
     */
    boolean approve(String approvalId, Long approverId);

    /**
     * 审批拒绝
     *
     * @param approvalId 审批ID
     * @param approverId 审批人ID
     * @param reason     拒绝原因
     * @return 是否成功
     */
    boolean reject(String approvalId, Long approverId, String reason);

    /**
     * 获取审批详情
     *
     * @param approvalId 审批ID
     * @return 审批详情JSON
     */
    String getApprovalDetail(String approvalId);

    /**
     * 等待审批结果（轮询方式，带超时）
     * 高级工具触发审批后，由本方法轮询等待审批结果
     *
     * @param approvalId 审批ID
     * @param timeoutSeconds 超时秒数
     * @param pollIntervalMs 轮询间隔（毫秒）
     * @return 审批最终状态
     */
    ToolExecutionResult.ApprovalStatus awaitApproval(String approvalId, long timeoutSeconds, long pollIntervalMs);
}
