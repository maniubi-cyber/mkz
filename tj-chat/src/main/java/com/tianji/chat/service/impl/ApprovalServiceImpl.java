package com.tianji.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianji.chat.domain.po.ApprovalRecord;
import com.tianji.chat.mapper.ApprovalRecordMapper;
import com.tianji.chat.service.ApprovalService;
import com.tianji.chat.tools.ToolExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 审批服务实现类
 *
 * 实现Human-in-the-Loop审批机制：
 * - Agent暂停并推送操作摘要至管理端
 * - 人工确认后方可继续
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ApprovalServiceImpl implements ApprovalService {

    private final ApprovalRecordMapper approvalRecordMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 审批记录Redis Key前缀
     */
    private static final String APPROVAL_KEY_PREFIX = "chat:approval:";

    /**
     * 审批记录过期时间（24小时）
     */
    private static final long APPROVAL_EXPIRE_HOURS = 24L;

    @Override
    public String createApprovalRequest(String toolName, String operationSummary, String parameters) {
        // 1. 生成审批ID
        String approvalId = generateApprovalId();

        // 2. 创建审批记录
        ApprovalRecord record = new ApprovalRecord();
        record.setApprovalId(approvalId);
        record.setToolName(toolName);
        record.setOperationSummary(operationSummary);
        record.setParameters(parameters);
        record.setApprovalStatusEnum(ToolExecutionResult.ApprovalStatus.PENDING);
        record.setCreatedAt(LocalDateTime.now());

        // 3. 保存到数据库
        approvalRecordMapper.insert(record);

        // 4. 缓存到Redis用于快速查询
        String redisKey = APPROVAL_KEY_PREFIX + approvalId;
        try {
            redisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(record),
                    APPROVAL_EXPIRE_HOURS, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.error("审批记录序列化失败", e);
        }

        log.info("创建审批请求成功，approvalId: {}, toolName: {}", approvalId, toolName);
        return approvalId;
    }

    @Override
    public ToolExecutionResult.ApprovalStatus getApprovalStatus(String approvalId) {
        // 1. 先从Redis查询
        String redisKey = APPROVAL_KEY_PREFIX + approvalId;
        String cachedData = redisTemplate.opsForValue().get(redisKey);

        if (cachedData != null) {
            try {
                ApprovalRecord record = objectMapper.readValue(cachedData, ApprovalRecord.class);
                return record.getApprovalStatusEnum();
            } catch (JsonProcessingException e) {
                log.warn("审批记录反序列化失败", e);
            }
        }

        // 2. 从数据库查询
        ApprovalRecord record = approvalRecordMapper.selectById(approvalId);
        if (record == null) {
            return null;
        }

        return record.getApprovalStatusEnum();
    }

    @Override
    public boolean approve(String approvalId, Long approverId) {
        return updateApprovalStatus(approvalId, approverId, ToolExecutionResult.ApprovalStatus.APPROVED, null);
    }

    @Override
    public boolean reject(String approvalId, Long approverId, String reason) {
        return updateApprovalStatus(approvalId, approverId, ToolExecutionResult.ApprovalStatus.REJECTED, reason);
    }

    @Override
    public String getApprovalDetail(String approvalId) {
        ApprovalRecord record = approvalRecordMapper.selectById(approvalId);
        if (record == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException e) {
            log.error("审批记录序列化失败", e);
            return null;
        }
    }

    @Override
    public ToolExecutionResult.ApprovalStatus awaitApproval(String approvalId, long timeoutSeconds, long pollIntervalMs) {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000;
        AtomicBoolean done = new AtomicBoolean(false);
        ToolExecutionResult.ApprovalStatus finalStatus = ToolExecutionResult.ApprovalStatus.PENDING;

        // 轮询等待审批结果
        while (!done.get() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            finalStatus = getApprovalStatus(approvalId);
            if (finalStatus == ToolExecutionResult.ApprovalStatus.APPROVED
                    || finalStatus == ToolExecutionResult.ApprovalStatus.REJECTED) {
                done.set(true);
                log.info("审批结果已返回，approvalId: {}, status: {}", approvalId, finalStatus);
            }
        }

        if (!done.get() && finalStatus == ToolExecutionResult.ApprovalStatus.PENDING) {
            log.warn("审批超时，approvalId: {}", approvalId);
        }
        return finalStatus;
    }

    /**
     * 更新审批状态
     */
    private boolean updateApprovalStatus(String approvalId, Long approverId,
                                         ToolExecutionResult.ApprovalStatus status, String comment) {
        // 1. 查询审批记录
        ApprovalRecord record = approvalRecordMapper.selectById(approvalId);
        if (record == null) {
            log.warn("审批记录不存在，approvalId: {}", approvalId);
            return false;
        }

        // 2. 检查状态
        if (record.getApprovalStatusEnum() != ToolExecutionResult.ApprovalStatus.PENDING) {
            log.warn("审批记录状态不是待审批，approvalId: {}, status: {}", approvalId, record.getApprovalStatus());
            return false;
        }

        // 3. 更新状态
        record.setApproverId(approverId);
        record.setApprovalStatusEnum(status);
        record.setApprovalComment(comment);
        record.setApprovedAt(LocalDateTime.now());

        // 4. 保存到数据库
        approvalRecordMapper.updateById(record);

        // 5. 更新Redis缓存
        String redisKey = APPROVAL_KEY_PREFIX + approvalId;
        try {
            redisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(record),
                    APPROVAL_EXPIRE_HOURS, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.error("审批记录序列化失败", e);
        }

        log.info("审批状态更新成功，approvalId: {}, status: {}", approvalId, status);
        return true;
    }

    /**
     * 生成审批ID
     */
    private String generateApprovalId() {
        return "APV-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 10000);
    }

    /**
     * 查询待审批列表
     *
     * @return 待审批记录列表
     */
    public Set<ApprovalRecord> getPendingApprovals() {
        LambdaQueryWrapper<ApprovalRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ApprovalRecord::getApprovalStatus,
                ToolExecutionResult.ApprovalStatus.PENDING.name())
                .orderByAsc(ApprovalRecord::getCreatedAt);

        return new java.util.HashSet<>(approvalRecordMapper.selectList(queryWrapper));
    }
}
