package com.mkz.chat.controller;

import com.mkz.chat.feign.AiBridgeClient;
import com.mkz.common.annotations.NoWrapper;
import com.mkz.common.domain.R;
import com.mkz.common.utils.UserContext;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 审批管理接口（HITL 人工审批）。
 * <p>
 * 薄网关层：用户鉴权在 Java 侧完成，审批数据与状态管理通过 Feign 调用 mkz-ai-bridge 桥接服务。
 * </p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/approval")
@Slf4j
@Api(tags = "审批管理接口")
public class ApprovalController {

    private final AiBridgeClient aiBridgeClient;

    @NoWrapper
    @ApiOperation("查询当前用户的待审批列表")
    @GetMapping("/pending")
    public R<List<Map<String, Object>>> pending() {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new RuntimeException("请先登录");
        }
        return aiBridgeClient.pendingApprovals(userId.toString());
    }

    @NoWrapper
    @ApiOperation("查询审批详情")
    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable("id") String id) {
        return aiBridgeClient.approvalDetail(id);
    }

    @NoWrapper
    @ApiOperation("审批通过")
    @PutMapping("/{id}/approve")
    public R<Map<String, Object>> approve(@PathVariable("id") String id) {
        return aiBridgeClient.approve(id);
    }

    @NoWrapper
    @ApiOperation("审批拒绝")
    @PutMapping("/{id}/reject")
    public R<Map<String, Object>> reject(@PathVariable("id") String id) {
        return aiBridgeClient.reject(id);
    }
}
