package com.mkz.pay.tasks;

import com.mkz.pay.domain.po.PayOrder;
import com.mkz.pay.domain.po.RefundOrder;
import com.mkz.pay.service.IReconciliationService;
import com.mkz.pay.service.impl.PayOrderServiceImpl;
import com.mkz.pay.service.impl.RefundOrderServiceImpl;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationTask {

    private final IReconciliationService reconciliationService;

    /**
     * 定时任务：每小时执行一次支付订单和退款订单对账
     */
//    @Scheduled(cron = "0 0 * * * ?")
    @XxlJob("reconcileOrders")
    public void reconcileOrders() {
        reconciliationService.reconcilePayOrders();
        reconciliationService.reconcileRefundOrders();
    }
}