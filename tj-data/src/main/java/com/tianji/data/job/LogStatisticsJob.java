package com.tianji.data.job;

import com.tianji.data.influxdb.domain.BusinessLog;
import com.tianji.data.mapper.BusinessLogMapper;
import com.tianji.data.service.IBusinessReportService;
import com.tianji.data.utils.TimeHandlerUtils;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Author: fsq
 * @Date: 2025/5/22 16:48
 * @Version: 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogStatisticsJob {

    private final BusinessLogMapper businessLogMapper;
    private final IBusinessReportService businessReportService;

    /**
     * 每天执行统计前一天的数据
     * 将前一天的influxdb数据进行统计
     * 以统计结果形式保存到mysql中
     */
    @XxlJob("logStatisticsToMySQL")
    public void logStatisticsToMySQL(){
        log.info("开始保存统计数据从InfluxDB到MySQL");
        //获取前一小时的时间范围
        String begin = TimeHandlerUtils.getYesterdayTime().getBegin();
        String end = TimeHandlerUtils.getYesterdayTime().getEnd();
        List<BusinessLog> list =businessLogMapper.getAllLogsByTime(begin, end);
        businessReportService.saveLogs(list);
        //TODO influxdb只支持按照时间范围删除数据，这里先不删除了，反正配置了过期策略（7天）
        log.info("保存统计数据从InfluxDB到MySQL成功");
    }
}
