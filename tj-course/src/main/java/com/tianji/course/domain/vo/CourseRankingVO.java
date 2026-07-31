package com.tianji.course.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 课程排行榜视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseRankingVO {

    /**
     * 课程ID
     */
    private Long courseId;

    /**
     * 课程名称
     */
    private String courseName;

    /**
     * 课程封面
     */
    private String coverUrl;

    /**
     * 排名
     */
    private Long rank;

    /**
     * 分数（综合热度分）
     */
    private Double score;

    /**
     * 观看人数
     */
    private Integer viewCount;

    /**
     * 点赞数
     */
    private Integer likeCount;

    /**
     * 赛季ID
     */
    private String seasonId;
}
