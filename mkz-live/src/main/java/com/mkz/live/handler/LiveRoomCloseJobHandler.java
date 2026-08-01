package com.mkz.live.handler;

import com.mkz.live.service.ILiveRoomService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 直播定时关播任务
 * <p>
 * 定时扫描处于"直播中"（status=1）但已过计划结束时间（end_time）的直播间并自动关闭：
 * 状态置为"已结束"（status=2）、补齐实际结束时间、发布 live.stop 消息。
 * 防止直播结束流程漏触发（如主播未手动关播）导致状态长期卡在"直播中"。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LiveRoomCloseJobHandler {

    private final ILiveRoomService liveRoomService;

    /**
     * 定时关播：由 XXL-Job 周期性触发（建议每 5-10 分钟）
     */
    @XxlJob("liveRoomCloseJobHandler")
    public void closeExpiredLiveRooms() {
        log.info("开始执行直播定时关播任务");
        try {
            int count = liveRoomService.closeExpiredRooms();
            log.info("直播定时关播任务完成，共关闭 {} 个超时直播间", count);
            XxlJobHelper.handleSuccess("关闭超时直播间 " + count + " 个");
        } catch (Exception e) {
            log.error("直播定时关播任务执行异常", e);
            XxlJobHelper.handleFail("定时关播失败: " + e.getMessage());
        }
    }
}
