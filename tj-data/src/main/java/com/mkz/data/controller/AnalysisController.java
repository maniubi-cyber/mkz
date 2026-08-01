package com.mkz.data.controller;/**
 * @author fsq
 * @date 2025/6/22 15:49
 */

import com.mkz.common.domain.dto.PageDTO;
import com.mkz.common.domain.query.PageQuery;
import com.mkz.data.model.po.CourseProfile;
import com.mkz.data.model.query.FlowQuery;
import com.mkz.data.model.vo.CourseProfileVO;
import com.mkz.data.model.vo.EchartsVO;
import com.mkz.data.model.vo.FunnelPlotChartsVO;
import com.mkz.data.model.vo.UserProfileVO;
import com.mkz.data.service.IAnalysisService;
import com.mkz.data.service.IFlowService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @Author: fsq
 * @Date: 2025/6/22 15:49
 * @Version: 1.0
 */
@RestController
@RequestMapping("/data/analysis")
@Api(tags = "数据分析相关操作")
@Slf4j
public class AnalysisController {

    @Autowired
    private IAnalysisService analysisService;

    /**
     * 数据展示：课程流程转换漏斗
     * @param query
     * @return
     */
    @GetMapping("/course/conversion")
    @ApiOperation(value = "课程流程转换漏斗", notes = "课程流程转换漏斗")
    public FunnelPlotChartsVO courseConversionDpv(FlowQuery query) {
        return analysisService.courseConversionDpv(query);
    }

    /**
     * 数据展示：日课程详情访问数性别分布
     * @param query
     * @return
     */
    @GetMapping("/course/gender")
    @ApiOperation(value = "课程访问数性别分布", notes = "课程访问数性别分布")
    public EchartsVO courseDetailGenderDuv(FlowQuery query) {
        return analysisService.courseDetailGenderDuv(query);
    }

    /**
     * 数据展示：日课程详情访问数省分布
     * @param query
     * @return
     */
    @GetMapping("/course/province")
    @ApiOperation(value = "课程访问数省分布", notes = "课程访问数省分布")
    public EchartsVO courseDetailProvinceDuv(FlowQuery query) {
        return analysisService.courseDetailProvinceDuv(query);
    }

    /**
     * 数据展示：分页查看课程画像
     * @return
     */
    @GetMapping("/course/profile")
    @ApiOperation(value = "分页查看课程画像", notes = "分页查看课程画像")
    public PageDTO<CourseProfileVO> getAnalysisResultByCourse(PageQuery query) {
        return analysisService.getAnalysisResultByCourse(query);
    }

    /**
     * 数据展示：分页查看用户画像
     * @return
     */
    @GetMapping("/user/profile")
    @ApiOperation(value = "分页查看用户画像", notes = "分页查看用户画像")
    public PageDTO<UserProfileVO> getAnalysisResultByUser(PageQuery query) {
        return analysisService.getAnalysisResultByUser(query);
    }

}
