package com.mkz.data.service;

import com.mkz.common.domain.R;
import com.mkz.common.domain.dto.PageDTO;
import com.mkz.data.influxdb.domain.BusinessLog;
import com.mkz.data.model.query.UrlPageQuery;
import com.mkz.data.model.query.UrlQuery;
import com.mkz.data.model.vo.EchartsVO;
import org.springframework.http.ResponseEntity;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * @ClassName IBusinessLogService.java
 * @Description 日志服务接口
 */
public interface IUrlLogService {

    /**
     * 分析指定url的访问数据
     * @param query
     * @return
     */
    PageDTO<BusinessLog> getLogsPageByUrl(UrlPageQuery  query);

    /**
     * 分析模糊url的访问数据
     * @param query
     * @return
     */
    PageDTO<BusinessLog> getLogsPageByUrlByLike(UrlPageQuery query);

    /**
     * 获取指定url的指标数据
     * @param query
     * @return
     */
    EchartsVO getMetricByUrl(UrlQuery query);

    /**
     * 获取模糊url的指标数据
     * @param query
     * @return
     */
    EchartsVO getMetricByUrlByLike(UrlQuery query);

    /**
     * 导出全部日志数据
     * @return
     */
    void exportLogs(HttpServletResponse response) throws IOException;
}
