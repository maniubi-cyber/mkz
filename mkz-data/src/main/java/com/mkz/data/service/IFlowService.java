package com.mkz.data.service;

import com.mkz.common.domain.dto.PageDTO;
import com.mkz.data.influxdb.domain.BusinessLog;
import com.mkz.data.model.query.FlowQuery;
import com.mkz.data.model.query.UrlPageQuery;
import com.mkz.data.model.query.UrlQuery;
import com.mkz.data.model.vo.EchartsVO;

/**
 * @ClassName IFlowService.java
 * @Description 流量统计服务接口
 */
public interface IFlowService {

    EchartsVO dnu(FlowQuery query);
    EchartsVO dpv(FlowQuery query);
    EchartsVO duv(FlowQuery query);
    EchartsVO dau(FlowQuery query);

    EchartsVO dpvTime(FlowQuery query);

    EchartsVO dauProvince(FlowQuery query);
}
