package com.mkz.data.service;

import com.mkz.data.influxdb.domain.BusinessLog;

import java.time.LocalDate;
import java.util.List;

/**
 * @ClassName IBusinessReportService.java
 * @Description 日志持久化到MySQL接口
 */
public interface IBusinessReportService {

    /**
     * 将日志统计数据落库
     *
     * @param list       日志数据（通常是昨日数据）
     * @param reportDate 统计数据所属日期（必须与 list 的数据日期一致，避免报表日期错位）
     */
    void saveLogs(List<BusinessLog> list, LocalDate reportDate);
}
