package com.mkz.data.job;

import com.mkz.data.influxdb.domain.BusinessLog;
import com.mkz.data.mapper.FlowMapper;
import com.mkz.data.model.po.LogAnalysisResult;
import com.mkz.data.service.IAnalysisService;
import com.mkz.data.utils.TimeHandlerUtils;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Author: fsq
 * @Date: 2025/6/23 19:00
 * @Version: 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogAnalysisJob {

    private final FlowMapper flowMapper;
    private final IAnalysisService analysisService;

    /**
     * 每天执行统计前一天的数据
     * 将前一天的influxdb数据进行分析、建立用户画像
     */
    @XxlJob("logAnalysis")
    public void logAnalysis(){
        log.info("开始分析日志数据，建立用户画像");
        //获取昨日的时间范围
        String begin = TimeHandlerUtils.getYesterdayTime().getBegin();
        String end = TimeHandlerUtils.getYesterdayTime().getEnd();
        List<BusinessLog> list =  flowMapper.courseDetailList(begin, end);
        LogAnalysisResult logAnalysisResult = analysisService.analyzeLogs(list);

        log.info("建立用户画像成功，分析日志数：{}", list == null ? 0 : list.size());
        // TODO 画像结果对外消费（原 MQ 发送已删除：DATA_ANALYSIS_LOG_KEY 全仓无消费端，属于死链路）。
        //  若后续搜索侧需要按画像个性化排序，在此发送并补充消费端后再落地。
    }
}
