package com.mkz.course.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 课程排行榜历史数据实体（冷数据存储）
 * 
 * 用于存储已归档的赛季排行榜数据
 */
@Data
@TableName("course_ranking_history")
public class CourseRanking {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 课程ID
     */
    private Long courseId;

    /**
     * 总分数
     */
    private Double totalScore;

    /**
     * 排名
     */
    private Integer rank;

    /**
     * 赛季ID
     */
    private String seasonId;

    /**
     * 归档时间
     */
    private LocalDateTime createdAt;
}
