package com.mkz.course.controller;

import com.mkz.course.domain.vo.CourseRankingVO;
import com.mkz.course.service.ICourseRankingService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 课程排行榜接口
 *
 * 基于Redis ZSet实现的当季课程热度排行榜，支撑毫秒级查询
 */
@Api(tags = "课程排行榜接口")
@RestController
@RequestMapping("courses/ranking")
@Slf4j
@RequiredArgsConstructor
public class CourseRankingController {

    private final ICourseRankingService courseRankingService;

    /**
     * 查询当季课程排行榜Top N
     * @param topN 前N名，默认10
     */
    @GetMapping
    @ApiOperation("查询当季课程排行榜Top N")
    public List<CourseRankingVO> getCurrentSeasonRanking(@RequestParam(defaultValue = "10") int topN) {
        return courseRankingService.getCurrentSeasonRanking(topN);
    }

    /**
     * 查询某课程当季排名
     * @param courseId 课程id
     * @return 排名（从1开始），未上榜返回null
     */
    @GetMapping("{courseId}")
    @ApiOperation("查询某课程当季排名")
    public Long getCourseRank(@PathVariable("courseId") Long courseId) {
        return courseRankingService.getCourseRank(courseId);
    }
}