package com.tianji.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.course.domain.po.CourseRanking;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 课程排行榜历史数据Mapper
 */
public interface CourseRankingMapper extends BaseMapper<CourseRanking> {

    /**
     * 根据赛季ID查询排行榜
     *
     * @param seasonId 赛季ID
     * @param limit    限制数量
     * @return 排行榜列表
     */
    @Select("SELECT * FROM course_ranking_history WHERE season_id = #{seasonId} ORDER BY total_score DESC LIMIT #{limit}")
    List<CourseRanking> selectBySeasonId(@Param("seasonId") String seasonId, @Param("limit") int limit);

    /**
     * 查询所有历史赛季ID
     *
     * @return 赛季ID列表
     */
    @Select("SELECT DISTINCT season_id FROM course_ranking_history")
    List<String> selectAllSeasonIds();
}
