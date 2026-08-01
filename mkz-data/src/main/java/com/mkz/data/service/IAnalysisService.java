package com.mkz.data.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mkz.common.domain.dto.PageDTO;
import com.mkz.common.domain.query.PageQuery;
import com.mkz.data.influxdb.domain.BusinessLog;
import com.mkz.data.model.po.Dnu;
import com.mkz.data.model.po.LogAnalysisResult;
import com.mkz.data.model.po.UserProfile;
import com.mkz.data.model.query.FlowQuery;
import com.mkz.data.model.vo.CourseProfileVO;
import com.mkz.data.model.vo.EchartsVO;
import com.mkz.data.model.vo.FunnelPlotChartsVO;
import com.mkz.data.model.vo.UserProfileVO;

import java.util.List;

/**
 * @Description：数据分析服务类接口
 */
public interface IAnalysisService {

    FunnelPlotChartsVO courseConversionDpv(FlowQuery query);

    EchartsVO courseDetailGenderDuv(FlowQuery query);

    EchartsVO courseDetailProvinceDuv(FlowQuery query);

    LogAnalysisResult analyzeLogs(List<BusinessLog> logs);

    PageDTO<UserProfileVO> getAnalysisResultByUser(PageQuery query);

    PageDTO<CourseProfileVO> getAnalysisResultByCourse(PageQuery query);
}
