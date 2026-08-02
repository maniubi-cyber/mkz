package com.mkz.data.job;

import com.mkz.data.influxdb.domain.BusinessLog;
import com.mkz.data.influxdb.domain.dto.TimeDTO;
import com.mkz.data.mapper.BusinessLogMapper;
import com.mkz.data.service.IBusinessReportService;
import com.mkz.data.utils.TimeHandlerUtils;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        //获取昨日的时间范围
        TimeDTO yesterday = TimeHandlerUtils.getYesterdayTime();
        String begin = yesterday.getBegin();
        String end = yesterday.getEnd();
        List<BusinessLog> list =businessLogMapper.getAllLogsByTime(begin, end);
        // 落库时以数据所属日期（昨日）作为统计日期，避免报表日期错位一天
        businessReportService.saveLogs(list, yesterday.getTargetDate());
        //TODO influxdb只支持按照时间范围删除数据，这里先不删除了，反正配置了过期策略（7天）
        log.info("保存统计数据从InfluxDB到MySQL成功");
    }
}
