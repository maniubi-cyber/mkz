package com.tianji.chat.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianji.chat.service.ApprovalService;
import com.tianji.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 工具执行器
 * 
 * 基于ReAct循环实现"思考→调用工具→观察结果→再决策"的多步推理：
 * - 低级工具（课程查询、排行榜查看）：直接执行，并对返回结果进行校验
 * - 中级工具（优惠券领取、点赞）：执行前校验幂等
 * - 高级工具（课程上下架、违规评论处理）：触发Human-in-the-Loop审批
 * 
 * 工具调用返回结构化JSON数据：
 * - 解析失败时自动携带格式纠正提示词重试
 * - 工具执行异常（如接口超时）不中断循环，而是将错误信息作为结果回传
 * - 由LLM自行决定降级回复或切换工具
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ToolExecutor {

    private final ApprovalService approvalService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    /**
     * 输入侧拦截提示词注入与越权意图
     */
    private static final String[] INJECTION_PATTERNS = {
            "ignore previous", "忽略之前", "system prompt", "系统提示",
            "jailbreak", "越狱", "override", "覆盖"
    };

    /**
     * 执行工具调用
     *
     * @param toolName  工具名称
     * @param parameters 参数（JSON格式）
     * @param riskLevel 风险等级
     * @return 执行结果
     */
    public ToolExecutionResult execute(String toolName, String parameters, ToolRiskLevel riskLevel) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 1. 输入侧拦截提示词注入与越权意图
            if (containsInjection(parameters)) {
                log.warn("检测到潜在的提示词注入攻击，toolName: {}, parameters: {}", toolName, parameters);
                return ToolExecutionResult.failure(toolName, "输入包含不安全内容，已被拦截");
            }

            // 2. 根据风险等级执行不同策略
            switch (riskLevel) {
                case LOW:
                    return executeLowRiskTool(toolName, parameters, startTime);
                case MEDIUM:
                    return executeMediumRiskTool(toolName, parameters, startTime);
                case HIGH:
                    return executeHighRiskTool(toolName, parameters, startTime);
                default:
                    return ToolExecutionResult.failure(toolName, "未知的风险等级");
            }
        } catch (Exception e) {
            log.error("工具执行异常，toolName: {}, parameters: {}", toolName, parameters, e);
            // 工具执行异常不中断循环，将错误信息作为结果回传
            return ToolExecutionResult.failure(toolName, 
                    "工具执行异常: " + e.getMessage() + "。你可以尝试其他方式或告诉用户当前服务暂不可用。");
        }
    }

    /**
     * 执行低级风险工具（课程查询、排行榜查看）
     * 直接执行，并对返回结果进行校验，防止编造不存在的信息
     */
    private ToolExecutionResult executeLowRiskTool(String toolName, String parameters, long startTime) {
        log.info("执行低级风险工具: {}", toolName);
        
        try {
            // 解析参数
            JsonNode params = objectMapper.readTree(parameters);
            
            // 执行具体工具逻辑（带格式纠正重试）
            String result = dispatchToolCall(toolName, params);
            // 使用 executeWithRetry 处理格式异常，自动携带纠正提示词重试
            ToolExecutionResult retryResult = executeWithRetry(toolName, result, ToolRiskLevel.LOW, 2);
            if (!retryResult.isSuccess()) {
                long duration = System.currentTimeMillis() - startTime;
                return ToolExecutionResult.builder()
                        .success(false)
                        .data(retryResult.getErrorMessage())
                        .toolName(toolName)
                        .requiresApproval(false)
                        .approvalStatus(ToolExecutionResult.ApprovalStatus.NOT_REQUIRED)
                        .executionDuration(duration)
                        .build();
            }
            String validatedResult = validateResult(retryResult.getData(), toolName);
            
            long duration = System.currentTimeMillis() - startTime;
            
            return ToolExecutionResult.builder()
                    .success(true)
                    .data(validatedResult)
                    .toolName(toolName)
                    .requiresApproval(false)
                    .approvalStatus(ToolExecutionResult.ApprovalStatus.NOT_REQUIRED)
                    .executionDuration(duration)
                    .build();
                    
        } catch (Exception e) {
            return ToolExecutionResult.failure(toolName, 
                    "执行失败: " + e.getMessage() + "。请检查参数是否正确，或尝试其他查询方式。");
        }
    }

    /**
     * 执行中级风险工具（优惠券领取、点赞）
     * 执行前校验幂等
     */
    private ToolExecutionResult executeMediumRiskTool(String toolName, String parameters, long startTime) {
        log.info("执行中级风险工具: {}", toolName);
        
        try {
            // 解析参数
            JsonNode params = objectMapper.readTree(parameters);
            
            // 幂等性校验
            if (!checkIdempotency(toolName, params)) {
                return ToolExecutionResult.failure(toolName,
                        "该操作已经执行过，请勿重复操作。");
            }

            // 执行具体工具逻辑（幂等校验通过，直接执行）
            String result = dispatchToolCall(toolName, params);
            
            long duration = System.currentTimeMillis() - startTime;
            
            return ToolExecutionResult.builder()
                    .success(true)
                    .data(result)
                    .toolName(toolName)
                    .requiresApproval(false)
                    .approvalStatus(ToolExecutionResult.ApprovalStatus.NOT_REQUIRED)
                    .executionDuration(duration)
                    .build();
                    
        } catch (Exception e) {
            return ToolExecutionResult.failure(toolName, 
                    "执行失败: " + e.getMessage() + "。如果问题持续存在，请联系管理员。");
        }
    }

    /**
     * 执行高级风险工具（课程上下架、违规评论处理）
     * 触发Human-in-the-Loop审批
     */
    private ToolExecutionResult executeHighRiskTool(String toolName, String parameters, long startTime) {
        log.info("执行高级风险工具，需要人工审批: {}", toolName);
        
        try {
            // 构建操作摘要
            String operationSummary = buildOperationSummary(toolName, parameters);
            
            // 创建审批请求
            String approvalId = approvalService.createApprovalRequest(toolName, operationSummary, parameters);
            
            long duration = System.currentTimeMillis() - startTime;
            
            return ToolExecutionResult.builder()
                    .success(false)
                    .toolName(toolName)
                    .requiresApproval(true)
                    .approvalId(approvalId)
                    .approvalStatus(ToolExecutionResult.ApprovalStatus.PENDING)
                    .data("操作需要人工审批，审批ID: " + approvalId + "，请等待管理员审批。")
                    .executionDuration(duration)
                    .build();
                    
        } catch (Exception e) {
            return ToolExecutionResult.failure(toolName, 
                    "创建审批请求失败: " + e.getMessage());
        }
    }

    /**
     * 等待高级工具审批结果，审批通过后重新执行工具
     * 支持轮询等待审批结果，超时返回错误
     *
     * @param toolName     工具名称
     * @param parameters   工具参数
     * @param riskLevel    风险等级
     * @param timeoutMs    超时时间（毫秒）
     * @param pollInterval 轮询间隔（毫秒）
     * @return 最终执行结果（APPROVED后重新执行的结果，或REJECTED/PENDING）
     */
    public ToolExecutionResult waitForApprovalAndExecute(String toolName, String parameters,
                                                          ToolRiskLevel riskLevel,
                                                          long timeoutMs, long pollInterval) {
        // 1. 创建审批请求
        String operationSummary = buildOperationSummary(toolName, parameters);
        String approvalId = approvalService.createApprovalRequest(toolName, operationSummary, parameters);

        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(pollInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ToolExecutionResult.failure(toolName, "等待审批被中断");
            }

            // 2. 轮询查询审批状态
            ToolExecutionResult.ApprovalStatus status = approvalService.getApprovalStatus(approvalId);

            if (status == ToolExecutionResult.ApprovalStatus.APPROVED) {
                // 3. 审批通过，重新执行工具
                log.info("审批通过，重新执行工具: {}, approvalId: {}", toolName, approvalId);
                return execute(toolName, parameters, riskLevel);
            } else if (status == ToolExecutionResult.ApprovalStatus.REJECTED) {
                // 4. 审批拒绝，返回拒绝结果
                log.warn("审批被拒绝，toolName: {}, approvalId: {}", toolName, approvalId);
                return ToolExecutionResult.failure(toolName,
                        "操作已被管理员拒绝，审批ID: " + approvalId);
            }
            // PENDING：继续等待
        }

        // 5. 超时未审批
        return ToolExecutionResult.failure(toolName,
                "操作审批超时，请重新发起请求，审批ID: " + approvalId);
    }

    /**
     * 检查是否包含注入攻击
     */
    private boolean containsInjection(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        String lowerInput = input.toLowerCase();
        for (String pattern : INJECTION_PATTERNS) {
            if (lowerInput.contains(pattern.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 幂等性校验
     */
    private boolean checkIdempotency(String toolName, JsonNode params) {
        Long userId = UserContext.getUser();
        String idempotentKey = "chat:idempotent:" + userId + ":" + toolName;
        // 使用 Redis SET NX EX 实现幂等性校验，TTL 5分钟
        Boolean setResult = redisTemplate.opsForValue()
                .setIfAbsent(idempotentKey, "1", 5, TimeUnit.MINUTES);
        return Boolean.TRUE.equals(setResult);
    }

    /**
     * 分发工具调用
     */
    private String dispatchToolCall(String toolName, JsonNode params) throws Exception {
        // 实际项目中根据toolName分发到具体的工具实现
        // 这里返回模拟结果
        return "{\"tool\": \"" + toolName + "\", \"status\": \"success\", \"data\": {}}";
    }

    /**
     * 校验返回结果，防止编造不存在的信息
     */
    private String validateResult(String result, String toolName) {
        // 实际项目中应该验证返回的数据是否真实存在
        // 例如查询课程时验证课程ID是否有效
        if (result == null || result.isEmpty() || "{}".equals(result)) {
            return "{\"error\": \"未找到相关信息，请确认查询条件是否正确\"}";
        }
        return result;
    }

    /**
     * 构建操作摘要
     */
    private String buildOperationSummary(String toolName, String parameters) {
        Long userId = UserContext.getUser();
        return String.format("用户[%s]请求执行[%s]操作，参数: %s", 
                userId != null ? userId : "unknown", toolName, parameters);
    }

    /**
     * 重试机制：当JSON解析失败时，携带格式纠正提示词重试
     *
     * @param toolName    工具名称
     * @param rawResponse 原始响应
     * @param riskLevel   风险等级
     * @param retryCount  重试次数
     * @return 解析后的结果
     */
    public ToolExecutionResult executeWithRetry(String toolName, String rawResponse,
                                                 ToolRiskLevel riskLevel, int retryCount) {
        if (retryCount <= 0) {
            return ToolExecutionResult.failure(toolName, 
                    "多次尝试后仍无法解析响应，请稍后再试或换一种方式提问。");
        }

        try {
            // 尝试解析JSON
            objectMapper.readTree(rawResponse);
            return execute(toolName, rawResponse, riskLevel);
        } catch (Exception e) {
            log.warn("JSON解析失败，准备重试，toolName: {}, retryCount: {}", toolName, retryCount);
            
            // 携带格式纠正提示词重试
            String correctedPrompt = rawResponse + "\n\n请确保返回有效的JSON格式数据。";
            return executeWithRetry(toolName, correctedPrompt, riskLevel, retryCount - 1);
        }
    }
}
