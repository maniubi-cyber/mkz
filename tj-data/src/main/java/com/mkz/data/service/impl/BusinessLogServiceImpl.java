package com.mkz.data.service.impl;

import com.mkz.common.exceptions.CommonException;
import com.mkz.common.utils.ExceptionsUtil;
import com.mkz.data.constants.LogBusinessEnum;
import com.mkz.data.influxdb.domain.BusinessLog;
import com.mkz.data.mapper.BusinessLogMapper;
import com.mkz.data.service.IBusinessLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @ClassName BusinessLogServiceImple.java
 * @Description 日志服务接口
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessLogServiceImpl implements IBusinessLogService {

    private final BusinessLogMapper businessLogMapper;

    @Override
    public Boolean createBusinessLog(BusinessLog businessLog) {
        try {
            //插入到influxDB中
            businessLogMapper.insertOne(businessLog);
            return true;
        } catch (Exception e) {
            log.error("数据埋点日志插入异常：{}", ExceptionsUtil.getStackTraceAsString(e));
            throw new CommonException(LogBusinessEnum.SAVE_FAIL.getMsg());
        }
    }

    @Override
    public Boolean createBusinessLogBatch(List<BusinessLog>  voList) {
        try {
            //插入到influxDB中
            businessLogMapper.insertBatch(voList);
            return true;
        } catch (Exception e) {
            log.error("数据埋点日志批量插入异常：{}", ExceptionsUtil.getStackTraceAsString(e));
            throw new CommonException(LogBusinessEnum.SAVE_FAIL.getMsg());
        }
    }
}
