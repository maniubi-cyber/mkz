/*
 Navicat Premium Dump SQL

 Source Server         : tianji
 Source Server Type    : MySQL
 Source Server Version : 80029 (8.0.29)
 Source Host           : 192.168.150.101:3306
 Source Schema         : tj_data

 Target Server Type    : MySQL
 Target Server Version : 80029 (8.0.29)
 File Encoding         : 65001

 Date: 26/08/2025 22:03:35
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for course_profile
-- ----------------------------
DROP TABLE IF EXISTS `course_profile`;
CREATE TABLE `course_profile`  (
  `id` bigint NOT NULL COMMENT '主键 ID',
  `course_id` bigint NULL DEFAULT NULL COMMENT '课程id',
  `sex_label` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '访问用户的性别标签',
  `province_labels` json NULL COMMENT '访问用户的省份标签 只存储前5个',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of course_profile
-- ----------------------------
INSERT INTO `course_profile` VALUES (1, 2, '女', '[\"河北省\"]', '2025-06-24 20:42:57', '2025-06-24 20:43:08');
INSERT INTO `course_profile` VALUES (2, 3, '男', '[\"北京市\"]', '2025-06-24 20:43:20', '2025-06-24 20:43:20');
INSERT INTO `course_profile` VALUES (3, 4, '女', '[\"四川省\"]', '2025-06-24 20:43:34', '2025-06-24 20:43:34');
INSERT INTO `course_profile` VALUES (4, 5, '女', '[\"江西省\"]', '2025-06-24 20:43:45', '2025-06-24 20:43:45');
INSERT INTO `course_profile` VALUES (5, 6, '女', '[\"浙江省\", \"黑龙江省\"]', '2025-06-24 20:44:00', '2025-06-24 21:01:43');
INSERT INTO `course_profile` VALUES (6, 7, '女', '[\"浙江省\"]', '2025-06-24 20:44:13', '2025-06-24 20:44:13');
INSERT INTO `course_profile` VALUES (7, 8, '女', '[\"四川省\"]', '2025-06-24 20:44:33', '2025-06-24 20:44:33');
INSERT INTO `course_profile` VALUES (1937487707207491585, 1, '男', '[\"天津市\"]', '2025-06-24 20:27:20', '2025-06-24 20:27:20');
INSERT INTO `course_profile` VALUES (1937487707333320705, 3, '男', '[\"天津市\"]', '2025-06-24 20:27:20', '2025-06-24 20:27:20');
INSERT INTO `course_profile` VALUES (1937487707459149826, 8, '男', '[\"天津市\"]', '2025-06-24 20:27:20', '2025-06-24 20:27:20');
INSERT INTO `course_profile` VALUES (1937487707522064386, 10, '男', '[\"天津市\"]', '2025-06-24 20:27:20', '2025-06-24 20:27:20');

-- ----------------------------
-- Table structure for tab_course_conversion_dpv
-- ----------------------------
DROP TABLE IF EXISTS `tab_course_conversion_dpv`;
CREATE TABLE `tab_course_conversion_dpv`  (
  `id` bigint NOT NULL COMMENT '主键 ID',
  `do_browse_dpv` bigint NULL DEFAULT NULL COMMENT '课程浏览次数',
  `do_order_dpv` bigint NULL DEFAULT NULL COMMENT '课程下单次数',
  `conversion_rate` double NULL DEFAULT NULL COMMENT '课程转化率',
  `report_time` date NULL DEFAULT NULL COMMENT '统计时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of tab_course_conversion_dpv
-- ----------------------------
INSERT INTO `tab_course_conversion_dpv` VALUES (1937086913077440514, 2, 0, 0, '2025-06-23', '2025-06-23 17:54:44', '2025-06-23 17:54:44');
INSERT INTO `tab_course_conversion_dpv` VALUES (1937496827402674177, 0, 0, 0, '2025-06-24', '2025-06-24 21:03:35', '2025-06-24 21:03:35');

-- ----------------------------
-- Table structure for tab_course_detail_gender_duv
-- ----------------------------
DROP TABLE IF EXISTS `tab_course_detail_gender_duv`;
CREATE TABLE `tab_course_detail_gender_duv`  (
  `id` bigint NOT NULL COMMENT '主键 ID',
  `man_dpv` bigint NULL DEFAULT NULL COMMENT '男：日课程详情访问数',
  `woman_dpv` bigint NULL DEFAULT NULL COMMENT '女：日课程详情访问数',
  `report_time` date NULL DEFAULT NULL COMMENT '统计时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of tab_course_detail_gender_duv
-- ----------------------------
INSERT INTO `tab_course_detail_gender_duv` VALUES (1937045494610546690, 2, 0, '2025-06-23', '2025-06-23 15:10:11', '2025-06-23 15:10:11');
INSERT INTO `tab_course_detail_gender_duv` VALUES (1937496827478171649, 0, 0, '2025-06-24', '2025-06-24 21:03:35', '2025-06-24 21:03:35');

-- ----------------------------
-- Table structure for tab_course_detail_province_duv
-- ----------------------------
DROP TABLE IF EXISTS `tab_course_detail_province_duv`;
CREATE TABLE `tab_course_detail_province_duv`  (
  `id` bigint NOT NULL COMMENT '主键 ID',
  `province_name` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '省',
  `duv` bigint NULL DEFAULT NULL COMMENT '省日课程用户访问数',
  `report_time` date NULL DEFAULT NULL COMMENT '统计时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of tab_course_detail_province_duv
-- ----------------------------
INSERT INTO `tab_course_detail_province_duv` VALUES (1937045495453601794, '天津市', 2, '2025-06-23', '2025-06-23 15:10:11', '2025-06-23 15:10:11');

-- ----------------------------
-- Table structure for tab_dau
-- ----------------------------
DROP TABLE IF EXISTS `tab_dau`;
CREATE TABLE `tab_dau`  (
  `id` bigint NOT NULL COMMENT '主键 ID',
  `all_dau` bigint NULL DEFAULT NULL COMMENT '总用户活跃数',
  `new_dau` bigint NULL DEFAULT NULL COMMENT '老用户活跃数',
  `old_dau` bigint NULL DEFAULT NULL COMMENT '新用户活跃数',
  `report_time` date NULL DEFAULT NULL COMMENT '统计时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of tab_dau
-- ----------------------------
INSERT INTO `tab_dau` VALUES (1937045132033937409, 5, 0, 5, '2025-06-23', '2025-06-23 15:08:44', '2025-06-23 15:08:44');
INSERT INTO `tab_dau` VALUES (1937496826752557057, 1, 0, 1, '2025-06-24', '2025-06-24 21:03:34', '2025-06-24 21:03:34');

-- ----------------------------
-- Table structure for tab_dau_province
-- ----------------------------
DROP TABLE IF EXISTS `tab_dau_province`;
CREATE TABLE `tab_dau_province`  (
  `id` bigint NOT NULL COMMENT '主键 ID',
  `dau` bigint NULL DEFAULT NULL COMMENT '活跃用户数',
  `province` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '省',
  `report_time` date NULL DEFAULT NULL COMMENT '统计时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uniq_province_report_time`(`province` ASC, `report_time` ASC) USING BTREE,
  INDEX `idx_report_time`(`report_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '活跃用户所属省份' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of tab_dau_province
-- ----------------------------
INSERT INTO `tab_dau_province` VALUES (1, 6, '天津市', '2025-06-22', '2025-06-23 17:03:18', '2025-06-23 17:03:18');
INSERT INTO `tab_dau_province` VALUES (1937496827142627330, 16, '', '2025-06-24', '2025-06-24 21:03:35', '2025-06-24 21:03:35');
INSERT INTO `tab_dau_province` VALUES (1937496827209736193, 33, '13', '2025-06-24', '2025-06-24 21:03:35', '2025-06-24 21:03:35');
INSERT INTO `tab_dau_province` VALUES (1937496827209736194, 11, '14', '2025-06-24', '2025-06-24 21:03:35', '2025-06-24 21:03:35');
INSERT INTO `tab_dau_province` VALUES (1937496827209736195, 6, '%E5%A4%A9%E6%B4%A5%E5%B8%82', '2025-06-24', '2025-06-24 21:03:35', '2025-06-24 21:03:35');
INSERT INTO `tab_dau_province` VALUES (1937496827272650754, 176, '天津市', '2025-06-24', '2025-06-24 21:03:35', '2025-06-24 21:03:35');
INSERT INTO `tab_dau_province` VALUES (1937496827272650755, 6, '%E5%B1%B1%E8%A5%BF%E7%9C%81', '2025-06-24', '2025-06-24 21:03:35', '2025-06-24 21:03:35');
INSERT INTO `tab_dau_province` VALUES (1937496827272650756, 16, '%E6%B2%B3%E5%8C%97%E7%9C%81', '2025-06-24', '2025-06-24 21:03:35', '2025-06-24 21:03:35');
INSERT INTO `tab_dau_province` VALUES (1937496827339759618, 1, '山西省', '2025-06-24', '2025-06-24 21:03:35', '2025-06-24 21:03:35');
INSERT INTO `tab_dau_province` VALUES (1937496827339759619, 5, '河北省', '2025-06-24', '2025-06-24 21:03:35', '2025-06-24 21:03:35');

-- ----------------------------
-- Table structure for tab_dau_range
-- ----------------------------
DROP TABLE IF EXISTS `tab_dau_range`;
CREATE TABLE `tab_dau_range`  (
  `id` bigint NOT NULL COMMENT '主键 ID',
  `dau_rang` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '活跃数区间',
  `user_num` bigint NULL DEFAULT NULL COMMENT '用户数',
  `report_time` date NULL DEFAULT NULL COMMENT '统计时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of tab_dau_range
-- ----------------------------
INSERT INTO `tab_dau_range` VALUES (1937045132033937410, '1-100人', 5, '2025-06-23', '2025-06-23 15:08:44', '2025-06-23 15:08:44');
INSERT INTO `tab_dau_range` VALUES (1937496826949689345, '1-100人', 1, '2025-06-24', '2025-06-24 21:03:34', '2025-06-24 21:03:34');

-- ----------------------------
-- Table structure for tab_dnu
-- ----------------------------
DROP TABLE IF EXISTS `tab_dnu`;
CREATE TABLE `tab_dnu`  (
  `id` bigint NOT NULL COMMENT '主键 ID',
  `dnu` bigint NULL DEFAULT NULL COMMENT '日新增用户数',
  `report_time` date NULL DEFAULT NULL COMMENT '统计时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of tab_dnu
-- ----------------------------
INSERT INTO `tab_dnu` VALUES (1937045131929079809, 0, '2025-06-23', '2025-06-23 15:08:44', '2025-06-23 15:08:44');
INSERT INTO `tab_dnu` VALUES (1937496826622533634, 0, '2025-06-24', '2025-06-24 21:03:34', '2025-06-24 21:03:34');

-- ----------------------------
-- Table structure for tab_dpv
-- ----------------------------
DROP TABLE IF EXISTS `tab_dpv`;
CREATE TABLE `tab_dpv`  (
  `id` bigint NOT NULL COMMENT '主键 ID',
  `dpv` bigint NULL DEFAULT NULL COMMENT '日访问页面量',
  `report_time` date NULL DEFAULT NULL COMMENT '统计时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of tab_dpv
-- ----------------------------
INSERT INTO `tab_dpv` VALUES (1937045131966828545, 119, '2025-06-23', '2025-06-23 15:08:44', '2025-06-23 15:08:44');
INSERT INTO `tab_dpv` VALUES (1937496826689642498, 1289, '2025-06-24', '2025-06-24 21:03:34', '2025-06-24 21:03:34');

-- ----------------------------
-- Table structure for tab_dpv_time
-- ----------------------------
DROP TABLE IF EXISTS `tab_dpv_time`;
CREATE TABLE `tab_dpv_time`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `mid_night_dpv` bigint NULL DEFAULT NULL COMMENT '0~7点活跃访问量数（不包括右区间）',
  `noon_dpv` bigint NULL DEFAULT NULL COMMENT '7~12点活跃访问量数（不包括右区间）',
  `afternoon_dpv` bigint NULL DEFAULT NULL COMMENT '12~18点活跃访问量数（不包括右区间）',
  `evening_dpv` bigint NULL DEFAULT NULL COMMENT '18~24点活跃访问量数（不包括右区间）',
  `report_time` date NULL DEFAULT NULL COMMENT '统计时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uniq_report_time`(`report_time` ASC) USING BTREE COMMENT '确保每个统计时间唯一'
) ENGINE = InnoDB AUTO_INCREMENT = 1937496827599806467 CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of tab_dpv_time
-- ----------------------------
INSERT INTO `tab_dpv_time` VALUES (1937086913274572801, 0, 0, 0, 119, '2025-06-23', '2025-06-23 17:54:44', '2025-06-23 17:54:44');
INSERT INTO `tab_dpv_time` VALUES (1937496827599806466, 0, 496, 300, 493, '2025-06-24', '2025-06-24 21:03:35', '2025-06-24 21:03:35');

-- ----------------------------
-- Table structure for tab_duv
-- ----------------------------
DROP TABLE IF EXISTS `tab_duv`;
CREATE TABLE `tab_duv`  (
  `id` bigint NOT NULL COMMENT '主键 ID',
  `duv` bigint NULL DEFAULT NULL COMMENT '日唯一访问量',
  `report_time` date NULL DEFAULT NULL COMMENT '统计时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of tab_duv
-- ----------------------------
INSERT INTO `tab_duv` VALUES (1937045131811639298, 1, '2025-06-23', '2025-06-23 15:08:44', '2025-06-23 15:08:44');
INSERT INTO `tab_duv` VALUES (1937496826580590593, 5, '2025-06-24', '2025-06-24 21:03:34', '2025-06-24 21:03:34');

-- ----------------------------
-- Table structure for user_profile
-- ----------------------------
DROP TABLE IF EXISTS `user_profile`;
CREATE TABLE `user_profile`  (
  `id` bigint NOT NULL COMMENT '主键 ID',
  `user_id` bigint NULL DEFAULT NULL COMMENT '用户id',
  `user_name` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '用户名称',
  `sex` int NULL DEFAULT NULL COMMENT '性别：0-男性，1-女性',
  `province` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '省',
  `course_labels` json NULL COMMENT '用户偏好  常访问课程id 前5',
  `free_label` int NULL DEFAULT NULL COMMENT '用户偏好  付费课程还是免费课程 0免费1付费',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user_profile
-- ----------------------------
INSERT INTO `user_profile` VALUES (1, 1628197515331371010, '小李', 1, '河北省', '[1]', 1, '2025-06-24 20:27:42', '2025-06-24 20:27:42');
INSERT INTO `user_profile` VALUES (1937487697476706305, 2, '杰克', 0, '天津市', '[8, 3, 12, 1, 10]', 0, '2025-06-24 20:27:18', '2025-06-24 20:27:18');

SET FOREIGN_KEY_CHECKS = 1;
