package com.tianji.data.job;/**
 * @author fsq
 * @date 2025/6/23 19:00
 */

import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.data.influxdb.domain.BusinessLog;
import com.tianji.data.mapper.BusinessLogMapper;
import com.tianji.data.mapper.FlowMapper;
import com.tianji.data.model.po.LogAnalysisResult;
import com.tianji.data.service.IAnalysisService;
import com.tianji.data.service.IFlowService;
import com.tianji.data.utils.TimeHandlerUtils;
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
    private final RabbitMqHelper mqHelper;

    /**
     * 每天执行统计前一天的数据
     * 将前一天的influxdb数据进行分析、用户画像
     */
    @XxlJob("logAnalysis")
    public void logAnalysis(){
        log.info("开始分析日志数据，建立用户画像");
        //获取前一小时的时间范围
        String begin = TimeHandlerUtils.getYesterdayTime().getBegin();
        String end = TimeHandlerUtils.getYesterdayTime().getEnd();
//        String end = TimeHandlerUtils.getTodayTime().getEnd();//TODO 这里为了测试写成今天
        List<BusinessLog> list =  flowMapper.courseDetailList(begin, end);
        LogAnalysisResult logAnalysisResult = analysisService.analyzeLogs(list);

        log.info("建立用户画像成功");
//        mqHelper.send(MqConstants.Exchange.DATA_EXCHANGE,
//                MqConstants.Key.DATA_ANALYSIS_LOG_KEY,
//                logAnalysisResult
//        );
//        log.info("发送用户画像分析结果到搜索微服务成功");

    }
}
