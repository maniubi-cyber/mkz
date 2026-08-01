package com.mkz.course.service;

import com.mkz.course.domain.vo.CourseRankingVO;

import java.util.List;

/**
 * 课程排行榜服务接口
 * 
 * 基于Redis ZSet实现实时排序，按赛季做冷热分层：
 * - 当季热数据常驻Redis支撑毫秒级查询
 * - 往季冷数据归档至MySQL释放内存
 */
public interface ICourseRankingService {

    /**
     * 更新课程排行分数（观看人数、点赞数等）
     *
     * @param courseId 课程ID
     * @param score    分数增量
     */
    void updateScore(Long courseId, double score);

    /**
     * 批量更新课程排行分数
     *
     * @param courseId 课程ID
     * @param score    分数增量
     * @param seasonId 赛季ID
     */
    void updateScore(Long courseId, double score, String seasonId);

    /**
     * 获取当前赛季排行榜Top N
     *
     * @param topN 前N名
     * @return 排行榜列表
     */
    List<CourseRankingVO> getCurrentSeasonRanking(int topN);

    /**
     * 获取指定赛季排行榜Top N
     *
     * @param seasonId 赛季ID
     * @param topN     前N名
     * @return 排行榜列表
     */
    List<CourseRankingVO> getSeasonRanking(String seasonId, int topN);

    /**
     * 获取课程在当前赛季的排名
     *
     * @param courseId 课程ID
     * @return 排名（从1开始）
     */
    Long getCourseRank(Long courseId);

    /**
     * 获取课程在指定赛季的排名
     *
     * @param courseId 课程ID
     * @param seasonId 赛季ID
     * @return 排名（从1开始）
     */
    Long getCourseRank(Long courseId, String seasonId);

    /**
     * 创建新赛季
     *
     * @param seasonId 赛季ID
     * @return 是否成功
     */
    boolean createNewSeason(String seasonId);

    /**
     * 归档历史赛季数据到MySQL
     *
     * @param seasonId 赛季ID
     * @return 归档的记录数
     */
    int archiveSeasonData(String seasonId);

    /**
     * 切换当前赛季
     *
     * @param newSeasonId 新赛季ID
     * @return 是否成功
     */
    boolean switchCurrentSeason(String newSeasonId);
}
