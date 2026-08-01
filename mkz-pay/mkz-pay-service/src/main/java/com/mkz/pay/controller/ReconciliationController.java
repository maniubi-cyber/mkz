package com.mkz.pay.controller;

import com.mkz.common.domain.dto.PageDTO;
import com.mkz.pay.domain.po.ReconciliationRecord;
import com.mkz.pay.domain.query.ReconciliationQueryDTO;
import com.mkz.pay.service.IReconciliationService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reconciliation")
@RequiredArgsConstructor
@Api(tags = "对账相关接口")
public class ReconciliationController {

    private final IReconciliationService reconciliationService;

    @GetMapping("/list")
    @ApiOperation("根据状态分页查询对账列表")
    public PageDTO<ReconciliationRecord> queryReconciliationList(ReconciliationQueryDTO queryDTO) {
        return reconciliationService.queryReconciliationList(queryDTO);
    }

    @GetMapping("/{id}")
    @ApiOperation("查询对账单详情")
    public ReconciliationRecord queryReconciliationDetail(@PathVariable("id") Long id) {
        return reconciliationService.getById(id);
    }


}