package com.mkz.course.constants;

/**
 * 排行榜相关常量
 * 
 * 基于Redis ZSet实现实时排序，按赛季做冷热分层：
 * - 当季热数据常驻Redis支撑毫秒级查询
 * - 往季冷数据归档至MySQL释放内存
 * - 由XXL-Job定期归档
 */
public class RankingConstants {

    /**
     * 课程排行榜Redis Key前缀
     */
    public static final String COURSE_RANKING_KEY_PREFIX = "ranking:course:";

    /**
     * 当前赛季排行榜Key
     */
    public static final String CURRENT_SEASON_RANKING_KEY = "ranking:course:current";

    /**
     * 赛季排行榜Key模板（用于历史赛季）
     */
    public static final String SEASON_RANKING_KEY_TEMPLATE = "ranking:course:season:{}";

    /**
     * 冷数据归档Key前缀（归档到MySQL的赛季数据）
     */
    public static final String COLD_DATA_KEY = "ranking:cold:season:{}";

    /**
     * 当前赛季ID
     */
    public static final String CURRENT_SEASON_ID = "current_season_id";

    /**
     * 排行榜默认Top N
     */
    public static final int DEFAULT_TOP_N = 100;

    /**
     * 排行榜缓存过期时间（当前赛季7天，确保赛季内数据常驻）
     */
    public static final long CURRENT_SEASON_EXPIRE_DAYS = 7L;

    /**
     * 历史赛季Redis缓存过期时间（24小时后归档到MySQL）
     */
    public static final long HISTORY_SEASON_EXPIRE_HOURS = 24L;

    /**
     * 赛季信息Redis Key
     */
    public static final String SEASON_INFO_KEY = "ranking:season:info";
}
