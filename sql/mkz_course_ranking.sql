-- 课程排行榜历史数据表（冷数据存储）
CREATE TABLE IF NOT EXISTS `course_ranking_history` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `course_id` BIGINT NOT NULL COMMENT '课程ID',
    `total_score` DOUBLE NOT NULL DEFAULT 0 COMMENT '总分数',
    `rank` INT DEFAULT NULL COMMENT '排名',
    `season_id` VARCHAR(50) NOT NULL COMMENT '赛季ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '归档时间',
    PRIMARY KEY (`id`),
    KEY `idx_season_rank` (`season_id`, `total_score` DESC),
    KEY `idx_course_season` (`course_id`, `season_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程排行榜历史数据';

-- 赛季信息表
CREATE TABLE IF NOT EXISTS `ranking_season_info` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `season_id` VARCHAR(50) NOT NULL COMMENT '赛季ID',
    `season_name` VARCHAR(100) DEFAULT NULL COMMENT '赛季名称',
    `start_date` DATETIME DEFAULT NULL COMMENT '开始时间',
    `end_date` DATETIME DEFAULT NULL COMMENT '结束时间',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-已归档 1-进行中',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_season_id` (`season_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='赛季信息表';
