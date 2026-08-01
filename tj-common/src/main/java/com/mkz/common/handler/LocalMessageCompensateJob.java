package com.mkz.common.handler;

import com.mkz.common.domain.po.LocalMessage;
import com.mkz.common.service.LocalMessageService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 本地消息表补偿任务
 * 定时扫描 local_message 表中状态为待发送(0)或发送失败(3)且重试次数未超限的消息，
 * 重新发送到 RocketMQ，保障消息最终一致性。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalMessageCompensateJob {

    private final LocalMessageService localMessageService;

    /**
     * 单次补偿扫描的最大消息数
     */
    private static final int BATCH_SIZE = 100;

    @XxlJob("localMessageCompensateJob")
    public void compensate() {
        log.info("[本地消息表补偿] 开始扫描待补偿消息...");
        // 1.查询待补偿消息（待发送/发送失败且到达重试时间、重试次数未超限）
        List<LocalMessage> pending = localMessageService.getPendingMessages(BATCH_SIZE);
        if (pending == null || pending.isEmpty()) {
            log.info("[本地消息表补偿] 无待补偿消息");
            return;
        }
        // 2.逐条补偿发送，单条异常不影响整批
        int compensated = 0;
        for (LocalMessage message : pending) {
            try {
                if (localMessageService.compensateMessage(message)) {
                    compensated++;
                }
            } catch (Exception e) {
                log.error("[本地消息表补偿] 补偿失败，id: {}", message.getId(), e);
            }
        }
        log.info("[本地消息表补偿] 本次扫描 {} 条，成功补偿 {} 条", pending.size(), compensated);
    }
}
