/*
 Navicat Premium Dump SQL

 Source Server         : tianji
 Source Server Type    : MySQL
 Source Server Version : 80029 (8.0.29)
 Source Host           : 192.168.150.101:3306
 Source Schema         : tj_learning

 Target Server Type    : MySQL
 Target Server Version : 80029 (8.0.29)
 File Encoding         : 65001

 Date: 26/08/2025 22:03:47
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for evaluation
-- ----------------------------
DROP TABLE IF EXISTS `evaluation`;
CREATE TABLE `evaluation`  (
  `id` bigint NOT NULL COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `course_id` bigint NOT NULL COMMENT '课程ID',
  `teacher_id` bigint NULL DEFAULT NULL COMMENT '教师ID（可选）',
  `content_rating` tinyint NOT NULL DEFAULT 0 COMMENT '内容评分(1-5)',
  `teaching_rating` tinyint NOT NULL DEFAULT 0 COMMENT '教学评分(1-5)',
  `difficulty_rating` tinyint NOT NULL DEFAULT 0 COMMENT '难度评分(1-5)',
  `value_rating` tinyint NOT NULL DEFAULT 0 COMMENT '价值评分(1-5)',
  `comment` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '评价内容',
  `is_anonymous` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否匿名',
  `help_count` int NOT NULL DEFAULT 0 COMMENT '有用次数',
  `hidden` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否被隐藏',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `unique_evaluation`(`id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户课程评价表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of evaluation
-- ----------------------------
INSERT INTO `evaluation` VALUES (1, 2, 2, NULL, 5, 1, 3, 4, '222', b'0', 0, b'0', '2025-05-22 14:00:47', '2025-05-22 14:46:38');
INSERT INTO `evaluation` VALUES (2, 129, 10, NULL, 4, 3, 3, 2, ' <span class=\"marg-rt-10\" @click=\"editNoteHandle(item)\" v-if=\"userInfo.id == item.authorId \">\r\n            <i class=\"iconfont zhy-a-icon-xiugai22x\"></i> 编辑\r\n          </span>\r\n          <span @click=\"gathersHandle(item)\" v-if=\"userInfo.id != item.authorId \" :class=\"{activeLiked:false && item.isGathered}\">\r\n            <i class=\"iconfont zhy-a-ico-caiji2x\"></i> {{item.isGathered ? \'已采集\' : \'采集\'}}\r\n          </span>\r\n          <span class=\"\" @click=\"delNoteHandle(item)\" v-if=\"userInfo.id == item.authorId \">\r\n            <i class=\"iconfont zhy-a-icon-delete22x\" style=\"font-size: 23px;top: 3px;\"></i> 删除\r\n          </span>', b'0', 0, b'0', '2025-05-22 14:43:40', '2025-05-22 19:13:12');
INSERT INTO `evaluation` VALUES (3, 5, 2, 1548889371405492225, 0, 0, 0, 0, '7666', b'0', 0, b'0', '2025-05-22 15:00:32', '2025-05-22 16:01:29');
INSERT INTO `evaluation` VALUES (5, 2, 10, NULL, 1, 5, 5, 5, '6666666666666261216666', b'1', 1, b'0', '2025-05-22 15:21:39', '2025-05-22 19:13:32');
INSERT INTO `evaluation` VALUES (6, 2, 1549025085494521857, NULL, 4, 5, 3, 5, '2222222大V22222222222222', b'0', 1, b'0', '2025-05-22 16:18:40', '2025-05-22 16:22:59');
INSERT INTO `evaluation` VALUES (9, 2, 3, NULL, 5, 5, 4, 3, '8888888888888888888888888', b'1', 1, b'0', '2025-05-22 19:22:38', '2025-05-22 19:22:55');
INSERT INTO `evaluation` VALUES (1925720630603821058, 129, 1549025085494521857, NULL, 5, 5, 2, 2, 'hneh1的课222222222222', b'0', 2, b'0', '2025-05-23 09:09:24', '2025-05-23 12:21:56');
INSERT INTO `evaluation` VALUES (1934579497433657345, 2, 8, NULL, 3, 4, 4, 3, '77777777777777', b'1', 1, b'0', '2025-06-16 19:51:09', '2025-06-16 19:51:55');
INSERT INTO `evaluation` VALUES (1954447979822284801, 2, 5, NULL, 4, 4, 4, 4, '6667777777777', b'1', 1, b'0', '2025-08-10 15:41:29', '2025-08-10 15:41:37');

-- ----------------------------
-- Table structure for interaction_question
-- ----------------------------
DROP TABLE IF EXISTS `interaction_question`;
CREATE TABLE `interaction_question`  (
  `id` bigint NOT NULL COMMENT '主键，互动问题的id',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '互动问题的标题',
  `description` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '问题描述信息',
  `course_id` bigint NOT NULL COMMENT '所属课程id',
  `chapter_id` bigint NOT NULL COMMENT '所属课程章id',
  `section_id` bigint NOT NULL COMMENT '所属课程节id',
  `user_id` bigint NOT NULL COMMENT '提问学员id',
  `latest_answer_id` bigint NULL DEFAULT NULL COMMENT '最新的一个回答的id',
  `answer_times` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '问题下的回答数量',
  `anonymity` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否匿名，默认false',
  `hidden` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否被隐藏，默认false',
  `status` tinyint NULL DEFAULT 0 COMMENT '管理端问题状态：0-未查看，1-已查看',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提问时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_course_id`(`course_id` ASC) USING BTREE,
  INDEX `section_id`(`section_id` ASC) USING BTREE,
  INDEX `user_id`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '互动提问的问题表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of interaction_question
-- ----------------------------
INSERT INTO `interaction_question` VALUES (1552212554946768897, 'redis安装的时候有问题，一直报错是怎么回事？', 'redis安装的时候有问题，总是报错是怎么回事？redis安装的时候有问题，总是报错是怎么回事？redis安装的时候有问题，总是报错是怎么回事？redis安装的时候有问题，总是报错是怎么回事？', 2, 15, 17, 5, 1548889371405492225, 0, b'1', b'0', 0, '2022-07-27 16:41:27', '2023-01-31 21:46:47');
INSERT INTO `interaction_question` VALUES (1585089140469317634, 'JDK哪里下载', '找不到网站啊老师', 2, 15, 16, 2, 1585178277083951106, 0, b'0', b'0', 1, '2022-10-26 10:01:16', '2023-01-31 21:46:47');
INSERT INTO `interaction_question` VALUES (1585589766919852033, 'Java的IO是阻塞IO吗？', '比如IO流中的数据读写', 2, 15, 17, 2, 1588103282121805825, 0, b'0', b'0', 1, '2022-10-27 12:31:44', '2023-10-24 23:06:11');
INSERT INTO `interaction_question` VALUES (1716474019450552321, '泛型是什么', '泛型是什么泛型是什么泛型是什么', 1549025085494521857, 1550383240983875588, 1550383240983875589, 2, 1717546735536455681, 3, b'0', b'0', 1, '2023-10-23 23:17:57', '2023-10-24 09:28:18');
INSERT INTO `interaction_question` VALUES (1716474282458578945, 'java泛型是什么匿名', '泛型是什么泛型是什么泛型是什么匿名', 1549025085494521857, 1550383240983875585, 1550383240983875586, 2, NULL, 5, b'1', b'0', 0, '2023-10-23 23:19:00', '2025-07-01 19:39:14');
INSERT INTO `interaction_question` VALUES (1923959226540494850, '好难啊', '真难', 3, 28, 29, 2, 1924042988347576321, 2, b'1', b'0', 1, '2025-05-18 12:30:05', '2025-05-18 12:30:05');
INSERT INTO `interaction_question` VALUES (1923959272858193922, '222', '222', 3, 28, 29, 2, 1924042473219936257, 4, b'1', b'0', 1, '2025-05-18 12:30:16', '2025-05-18 12:30:16');
INSERT INTO `interaction_question` VALUES (1925014317666148354, '6666', '666', 1549025085494521857, 1550383240983875585, 1550383240983875586, 2, NULL, 0, b'0', b'0', 1, '2025-05-21 10:22:43', '2025-05-21 10:22:43');
INSERT INTO `interaction_question` VALUES (1925014397701857282, '4444', '44', 1549025085494521857, 1550383240983875585, 1550383240983875587, 2, NULL, 0, b'0', b'0', 0, '2025-05-21 10:23:02', '2025-05-21 10:23:02');
INSERT INTO `interaction_question` VALUES (1925029758102937601, '666', '666', 2, 15, 16, 2, NULL, 0, b'0', b'0', 1, '2025-05-21 11:24:04', '2025-05-21 11:24:04');
INSERT INTO `interaction_question` VALUES (1925030128392871938, '555', '555', 2, 15, 16, 2, NULL, 0, b'0', b'0', 0, '2025-05-21 11:25:32', '2025-05-21 11:25:32');
INSERT INTO `interaction_question` VALUES (1925039588498743297, '好啊', '22', 2, 15, 16, 2, NULL, 0, b'1', b'0', 1, '2025-05-21 12:03:08', '2025-05-21 12:03:08');
INSERT INTO `interaction_question` VALUES (1925786409261174786, '325532434', '44343242423', 1549025085494521857, 1550383240983875585, 1550383240983875586, 129, NULL, 0, b'1', b'0', 1, '2025-05-23 13:30:46', '2025-05-23 13:30:46');
INSERT INTO `interaction_question` VALUES (1957047907219673090, 'java泛型是什么意思', 'java泛型是什么意思', 1549025085494521857, 1550383240983875588, 1550383240983875590, 2, NULL, 1, b'0', b'0', 0, '2025-08-17 19:52:37', '2025-08-17 19:52:40');

-- ----------------------------
-- Table structure for interaction_reply
-- ----------------------------
DROP TABLE IF EXISTS `interaction_reply`;
CREATE TABLE `interaction_reply`  (
  `id` bigint NOT NULL COMMENT '互动问题的回答id',
  `question_id` bigint NOT NULL COMMENT '互动问题问题id',
  `answer_id` bigint NULL DEFAULT 0 COMMENT '回复的上级回答id',
  `user_id` bigint NOT NULL COMMENT '回答者id',
  `content` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '回答内容',
  `target_user_id` bigint NULL DEFAULT 0 COMMENT '回复的目标用户id',
  `target_reply_id` bigint NULL DEFAULT 0 COMMENT '回复的目标回复id',
  `reply_times` int NOT NULL DEFAULT 0 COMMENT '评论数量',
  `liked_times` int NOT NULL DEFAULT 0 COMMENT '点赞数量',
  `hidden` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否被隐藏，默认false',
  `anonymity` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否匿名，默认false',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_question_id`(`question_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '互动问题的回答或评论' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of interaction_reply
-- ----------------------------
INSERT INTO `interaction_reply` VALUES (1548889371405492225, 1552212554946768897, 0, 1548889371405492225, '是不是Redis的依赖没有安装呢？', 0, 0, 2, 0, b'0', b'0', '2022-07-27 16:44:37', '2022-07-28 10:36:08');
INSERT INTO `interaction_reply` VALUES (1585177426969833473, 1585089140469317634, 0, 129, '同问啊，我也碰到这个问题了，老师，救救孩子吧', 2, 0, 0, 0, b'0', b'1', '2022-10-26 15:52:04', '2022-11-29 15:25:06');
INSERT INTO `interaction_reply` VALUES (1585178277083951106, 1585089140469317634, 0, 1548889371405492225, '安装完成后有没有配置环境变量呢？', 2, 0, 2, 0, b'0', b'0', '2022-10-26 15:55:27', '2022-10-27 10:10:56');
INSERT INTO `interaction_reply` VALUES (1585179315912388610, 1585089140469317634, 1585178277083951106, 2, '配置了，在Path中配置了JAVA_HOME', 1548889371405492225, 1585178277083951106, 0, 0, b'0', b'0', '2022-10-26 15:59:34', '2022-10-26 16:02:09');
INSERT INTO `interaction_reply` VALUES (1585180460118519809, 1585089140469317634, 1585178277083951106, 1548889371405492225, '如果确定环境变量没有配置错误的话，试试看关闭CMD窗口，再次打开', 2, 1585179315912388610, 2, 0, b'0', b'0', '2022-10-26 16:04:07', '2022-10-27 10:11:18');
INSERT INTO `interaction_reply` VALUES (1585183997506433026, 1585089140469317634, 1585178277083951106, 2, '老师牛啊，确实是这个问题。不过为什么要关闭呢？', 1548889371405492225, 1585178277083951106, 1, 0, b'0', b'0', '2022-10-26 16:18:10', '2022-10-27 10:27:55');
INSERT INTO `interaction_reply` VALUES (1585184256685060098, 1585089140469317634, 1585178277083951106, 129, '666，老师还是厉害啊', 1548889371405492225, 1585178277083951106, 0, 0, b'0', b'1', '2022-10-26 16:19:12', '2022-11-29 15:25:09');
INSERT INTO `interaction_reply` VALUES (1585184666292400129, 1585089140469317634, 1585178277083951106, 1548889371405492225, '因为CMD默认加载的还是你配置Path之前的环境变量，关闭重新打开后才会读取到最新的Path', 2, 1585183997506433026, 1, 0, b'0', b'0', '2022-10-26 16:20:50', '2022-10-27 10:26:50');
INSERT INTO `interaction_reply` VALUES (1585191151894343681, 1585089140469317634, 1585178277083951106, 2, '你个老6，为什么老匿名', 129, 1585184256685060098, 0, 0, b'0', b'0', '2022-10-26 16:46:36', '2022-11-29 15:25:13');
INSERT INTO `interaction_reply` VALUES (1588103282121805825, 1585589766919852033, 0, 1, '阻塞IO和非阻塞IO都有。java.io包下的都是阻塞IO，java.nio下的是非阻塞IO', 0, 0, 1, 1, b'0', b'0', '2022-11-03 16:35:32', '2025-05-25 14:19:46');
INSERT INTO `interaction_reply` VALUES (1588105119793188865, 1585589766919852033, 1588103282121805825, 2, '感谢老师的回复，我们课堂中没有讲过NIO，有没有资料啊', 0, 0, 0, 1, b'0', b'0', '2022-11-03 16:43:27', '2025-05-25 14:19:46');
INSERT INTO `interaction_reply` VALUES (1588110148121956353, 1585589766919852033, 1588103282121805825, 1, 'NIO资料可以联系客服MM获取哦', 2, 1588105119793188865, 0, 1, b'0', b'0', '2022-11-03 17:05:06', '2025-05-25 14:19:46');
INSERT INTO `interaction_reply` VALUES (1717050284524032001, 1716474019450552321, 0, 129, '2023年10月25日13:27:33萝丝', 2, 0, 1, 1, b'0', b'0', '2023-10-25 13:27:52', '2023-10-25 18:36:39');
INSERT INTO `interaction_reply` VALUES (1717050633020362754, 1716474282458578945, 0, 129, '我是rose，匿名一下', 2, 0, 0, 3, b'0', b'1', '2023-10-25 13:29:15', '2025-05-25 14:19:26');
INSERT INTO `interaction_reply` VALUES (1717050757574414337, 1716474019450552321, 1717050284524032001, 2, '还真是', 129, 1717050284524032001, 0, 0, b'0', b'0', '2023-10-25 13:29:45', '2023-10-25 18:36:56');
INSERT INTO `interaction_reply` VALUES (1717128524655566849, 1716474282458578945, 0, 2, '22', 2, 0, 2, 1, b'0', b'0', '2023-10-25 18:38:46', '2023-10-25 18:43:40');
INSERT INTO `interaction_reply` VALUES (1717545915369668609, 1716474282458578945, 0, 2, '是这样的！2023年10月26日22:17:09！', 2, 0, 0, 1, b'0', b'0', '2023-10-26 22:17:21', '2025-05-21 10:23:41');
INSERT INTO `interaction_reply` VALUES (1717546155967528962, 1716474019450552321, 0, 2, '2023年10月26日22:18:07', 2, 0, 0, 0, b'0', b'0', '2023-10-26 22:18:19', '2023-10-26 22:18:19');
INSERT INTO `interaction_reply` VALUES (1717546735536455681, 1716474019450552321, 0, 2, '测试2023年10月26日22:20:23积分', 2, 0, 0, 0, b'0', b'0', '2023-10-26 22:20:37', '2023-10-26 22:20:37');
INSERT INTO `interaction_reply` VALUES (1717550147976163329, 1716474282458578945, 0, 2, '积分啊', 2, 0, 0, 0, b'0', b'0', '2023-10-26 22:34:10', '2023-10-26 22:34:10');
INSERT INTO `interaction_reply` VALUES (1923999145044480001, 1923959226540494850, 0, 1, '加油哦！', 0, 0, 0, 1, b'0', b'0', '2025-05-18 15:08:42', '2025-05-18 16:40:39');
INSERT INTO `interaction_reply` VALUES (1924010847890837505, 1552212554946768897, 1548889371405492225, 2, '666', 1548889371405492225, 1548889371405492225, 0, 1, b'0', b'0', '2025-05-18 15:55:12', '2025-05-22 15:30:27');
INSERT INTO `interaction_reply` VALUES (1924010875116064769, 1552212554946768897, 1548889371405492225, 2, '666', 2, 1924010847890837505, 0, 0, b'0', b'0', '2025-05-18 15:55:19', '2025-05-18 15:55:19');
INSERT INTO `interaction_reply` VALUES (1924038006856597506, 1923959272858193922, 0, 2, '666', 2, 0, 0, 1, b'0', b'0', '2025-05-18 17:43:07', '2025-05-20 18:41:27');
INSERT INTO `interaction_reply` VALUES (1924038144387825666, 1923959272858193922, 0, 2, '222', 2, 0, 0, 0, b'0', b'0', '2025-05-18 17:43:40', '2025-05-18 17:43:40');
INSERT INTO `interaction_reply` VALUES (1924038263657054209, 1923959272858193922, 0, 2, '222', 2, 0, 0, 0, b'0', b'0', '2025-05-18 17:44:09', '2025-05-18 17:44:09');
INSERT INTO `interaction_reply` VALUES (1924042473219936257, 1923959272858193922, 0, 2, '555', 2, 0, 0, 0, b'0', b'0', '2025-05-18 18:00:52', '2025-05-18 18:00:52');
INSERT INTO `interaction_reply` VALUES (1924042988347576321, 1923959226540494850, 0, 2, '444', 2, 0, 0, 0, b'0', b'0', '2025-05-18 18:02:55', '2025-05-18 18:02:55');
INSERT INTO `interaction_reply` VALUES (1926523577843683330, 1585589766919852033, 1588103282121805825, 1, '666', 1, 1588110148121956353, 0, 1, b'0', b'0', '2025-05-25 14:19:51', '2025-05-25 14:19:56');
INSERT INTO `interaction_reply` VALUES (1930540341741043714, 1716474282458578945, 1717128524655566849, 2, '6', 2, 1717128524655566849, 0, 0, b'0', b'0', '2025-06-05 16:21:00', '2025-06-05 16:21:00');
INSERT INTO `interaction_reply` VALUES (1930552229044502530, 1716474282458578945, 1717128524655566849, 2, '你说的很对！', 2, 1930540341741043714, 0, 0, b'0', b'0', '2025-06-05 17:08:14', '2025-06-05 17:08:14');
INSERT INTO `interaction_reply` VALUES (1940012284755587073, 1716474282458578945, 0, 2, '7777', 2, 0, 0, 0, b'0', b'0', '2025-07-01 19:39:14', '2025-07-01 19:39:14');
INSERT INTO `interaction_reply` VALUES (1957047918917586945, 1957047907219673090, 0, 9999, 'Java泛型是一种在编程中用于增强类型安全性的机制。通过泛型，可以在编译时检测到类型错误，避免在运行时出现类型转换异常。泛型使得代码更加灵活和可重用，可以编写出更加通用的类和方法。在Java中，泛型使用尖括号<>来定义，可以用于类、接口和方法。例如，定义一个泛型类`List<T>`表示一个可以存储任意类型元素的列表。在实际使用时，可以指定具体的类型，比如`List<String>`表示一个存储字符串的列表。泛型的引入大大提高了Java程序的类型安全性和代码的可读性，是Java编程中的重要特性。', 0, 0, 1, 1, b'0', b'0', '2025-08-17 19:52:40', '2025-08-17 22:24:26');
INSERT INTO `interaction_reply` VALUES (1957086111528390658, 1957047907219673090, 1957047918917586945, 2, '这对吗', 9999, 1957047918917586945, 0, 0, b'0', b'0', '2025-08-17 22:24:26', '2025-08-17 22:24:26');

-- ----------------------------
-- Table structure for learning_lesson
-- ----------------------------
DROP TABLE IF EXISTS `learning_lesson`;
CREATE TABLE `learning_lesson`  (
  `id` bigint NOT NULL COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '学员id',
  `course_id` bigint NOT NULL COMMENT '课程id',
  `status` tinyint NULL DEFAULT 0 COMMENT '课程状态，0-未学习，1-学习中，2-已学完，3-已失效',
  `week_freq` tinyint NULL DEFAULT NULL COMMENT '每周学习频率，例如每周学习6小节，则频率为6',
  `plan_status` tinyint NOT NULL DEFAULT 0 COMMENT '学习计划状态，0-没有计划，1-计划进行中',
  `learned_sections` int NOT NULL DEFAULT 0 COMMENT '已学习小节数量',
  `latest_section_id` bigint NULL DEFAULT NULL COMMENT '最近一次学习的小节id',
  `latest_learn_time` datetime NULL DEFAULT NULL COMMENT '最近一次学习的时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `expire_time` datetime NULL DEFAULT NULL COMMENT '过期时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_user_id`(`user_id` ASC, `course_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '学生课程表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of learning_lesson
-- ----------------------------
INSERT INTO `learning_lesson` VALUES (1, 2, 2, 1, 1, 1, 12, 16, '2025-08-09 22:36:39', '2022-08-05 20:02:50', '2030-10-28 20:02:29', '2025-08-09 22:36:41');
INSERT INTO `learning_lesson` VALUES (2, 2, 3, 0, 1, 1, 3, 29, '2025-05-20 09:51:56', '2022-08-06 15:16:48', '2030-10-26 15:16:37', '2025-05-20 09:52:28');
INSERT INTO `learning_lesson` VALUES (1585170299127607297, 129, 2, 1, 1, 1, 0, 16, '2023-04-11 22:37:05', '2022-12-05 23:00:29', '2030-10-26 15:14:54', '2025-06-15 18:15:29');
INSERT INTO `learning_lesson` VALUES (1716416110066737154, 2, 1549025085494521857, 0, 3, 1, 1, 1550383240983875587, '2025-06-01 18:52:19', '2023-10-23 19:27:45', '2090-10-23 19:27:45', '2025-06-01 18:52:18');
INSERT INTO `learning_lesson` VALUES (1717050074318098433, 129, 1549025085494521857, 2, NULL, 0, 0, NULL, NULL, '2023-10-25 13:26:54', '2029-10-25 13:26:54', '2025-05-20 08:44:02');
INSERT INTO `learning_lesson` VALUES (1929790064511533058, 2, 1, 0, NULL, 0, 0, NULL, NULL, '2025-06-03 14:39:40', '2026-06-03 14:39:40', '2025-06-03 14:39:42');
INSERT INTO `learning_lesson` VALUES (1929790064519921665, 2, 5, 0, NULL, 0, 0, NULL, NULL, '2025-06-03 14:39:40', '2026-06-03 14:39:40', '2025-06-03 14:39:42');
INSERT INTO `learning_lesson` VALUES (1929794647916920834, 2, 1589888774267072513, 0, NULL, 0, 0, NULL, NULL, '2025-06-03 14:57:53', '2858-09-03 14:57:53', '2025-06-03 14:57:55');
INSERT INTO `learning_lesson` VALUES (1934225493058965506, 2, 10, 0, NULL, 0, 0, NULL, NULL, '2025-06-15 20:24:28', '2026-06-15 20:24:28', '2025-06-15 20:24:29');
INSERT INTO `learning_lesson` VALUES (1934550167114575873, 2, 8, 0, NULL, 0, 0, NULL, NULL, '2025-06-16 17:54:37', '2026-06-16 17:54:37', '2025-06-16 17:54:36');

-- ----------------------------
-- Table structure for learning_record
-- ----------------------------
DROP TABLE IF EXISTS `learning_record`;
CREATE TABLE `learning_record`  (
  `id` bigint NOT NULL COMMENT '学习记录的id',
  `lesson_id` bigint NOT NULL COMMENT '对应课表的id',
  `section_id` bigint NOT NULL COMMENT '对应小节的id',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `moment` int NULL DEFAULT 0 COMMENT '视频的当前观看时间点，单位秒',
  `finished` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否完成学习，默认false',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '第一次观看时间',
  `finish_time` datetime NULL DEFAULT NULL COMMENT '完成学习的时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（最近一次观看时间）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_update_time`(`update_time` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_lesson_id`(`lesson_id` ASC, `section_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '学习记录表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of learning_record
-- ----------------------------
INSERT INTO `learning_record` VALUES (1582555272977592322, 1, 16, 2, 0, b'1', '2022-10-19 10:12:34', '2022-10-19 10:45:55', '2025-08-09 22:36:41');
INSERT INTO `learning_record` VALUES (1582565729037791233, 1, 17, 2, 148, b'1', '2022-10-19 10:54:07', '2022-10-19 10:57:47', '2022-10-19 15:50:05');
INSERT INTO `learning_record` VALUES (1582572518466760706, 1, 18, 2, 14, b'1', '2022-10-19 11:21:06', '2022-10-19 14:52:50', '2022-10-27 12:05:14');
INSERT INTO `learning_record` VALUES (1582572534866489346, 1, 19, 2, 250, b'1', '2022-10-19 11:21:10', '2022-10-19 17:53:55', '2022-10-19 17:56:01');
INSERT INTO `learning_record` VALUES (1582572535164284930, 1, 20, 2, 135, b'1', '2022-10-19 11:21:10', '2022-10-19 17:58:09', '2022-10-27 12:07:12');
INSERT INTO `learning_record` VALUES (1582572537429209089, 1, 21, 2, 253, b'1', '2022-10-19 11:21:10', '2022-10-19 18:02:34', '2022-10-19 18:04:54');
INSERT INTO `learning_record` VALUES (1582674101338656770, 1, 22, 2, 257, b'1', '2022-10-19 18:04:45', '2022-10-19 18:07:15', '2022-10-19 18:09:35');
INSERT INTO `learning_record` VALUES (1582675262523326466, 1, 23, 2, 254, b'1', '2022-10-19 18:09:22', '2022-10-19 18:11:37', '2022-10-19 18:13:57');
INSERT INTO `learning_record` VALUES (1582676374013886465, 1, 24, 2, 250, b'1', '2022-10-19 18:13:47', '2022-10-19 18:16:02', '2022-10-20 11:36:50');
INSERT INTO `learning_record` VALUES (1582938844335001602, 1, 25, 2, 80, b'1', '2022-10-20 11:36:44', '2022-10-20 11:38:59', '2022-10-20 11:58:00');
INSERT INTO `learning_record` VALUES (1583012729738776577, 1, 26, 2, 262, b'1', '2022-10-20 16:30:20', '2022-10-20 16:30:21', '2022-10-20 16:38:39');
INSERT INTO `learning_record` VALUES (1586757474101342209, 1, 27, 2, 0, b'1', '2022-10-31 00:30:37', '2022-10-31 00:30:36', '2022-10-31 00:30:37');
INSERT INTO `learning_record` VALUES (1586757474101342309, 2, 29, 2, 116, b'0', '2022-10-31 00:30:37', NULL, '2025-05-18 12:30:13');
INSERT INTO `learning_record` VALUES (1599780755855228929, 1585170299127607297, 16, 129, 37, b'0', '2022-12-05 23:00:29', NULL, '2022-12-05 23:01:34');
INSERT INTO `learning_record` VALUES (1716416760003502082, 1716416110066737154, 1550383240983875586, 2, 172, b'1', '2023-10-23 19:30:26', '2023-10-23 19:31:41', '2023-10-23 21:05:06');
INSERT INTO `learning_record` VALUES (1717547403856855042, 1716416110066737154, 1550383240983875587, 2, 123, b'0', '2023-10-26 22:23:16', NULL, '2023-10-26 22:23:16');
INSERT INTO `learning_record` VALUES (1923980644472852482, 1716416110066737154, 1550383240983875589, 2, 0, b'0', '2025-05-18 13:55:11', NULL, '2025-05-18 13:55:11');
INSERT INTO `learning_record` VALUES (1923994522887917569, 2, 30, 2, 0, b'0', '2025-05-18 14:50:20', NULL, '2025-05-18 14:50:20');
INSERT INTO `learning_record` VALUES (1923994736742895618, 2, 39, 2, 0, b'0', '2025-05-18 14:51:11', NULL, '2025-05-18 14:51:11');
INSERT INTO `learning_record` VALUES (1924043820925308929, 1717050074318098433, 1550383240983875586, 129, 0, b'0', '2025-05-18 18:06:14', NULL, '2025-05-18 18:06:14');
INSERT INTO `learning_record` VALUES (1924644214114099202, 2, 35, 2, 0, b'0', '2025-05-20 09:52:02', NULL, '2025-05-20 09:52:02');

-- ----------------------------
-- Table structure for lesson_collect
-- ----------------------------
DROP TABLE IF EXISTS `lesson_collect`;
CREATE TABLE `lesson_collect`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '学员id',
  `course_id` bigint NOT NULL COMMENT '课程id',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_user_course`(`user_id` ASC, `course_id` ASC) USING BTREE,
  INDEX `idx_course_id`(`course_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1954457526725083139 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '学生课程收藏表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of lesson_collect
-- ----------------------------
INSERT INTO `lesson_collect` VALUES (4, 2, 2, '2025-05-22 18:58:57');
INSERT INTO `lesson_collect` VALUES (6, 129, 1589888774267072513, '2025-05-23 10:21:47');
INSERT INTO `lesson_collect` VALUES (1954447843574513666, 2, 5, '2025-08-10 15:40:56');
INSERT INTO `lesson_collect` VALUES (1954457526725083138, 2, 7, '2025-08-10 16:19:25');

-- ----------------------------
-- Table structure for note
-- ----------------------------
DROP TABLE IF EXISTS `note`;
CREATE TABLE `note`  (
  `id` bigint NOT NULL DEFAULT 0 COMMENT '笔记id',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `course_id` bigint NULL DEFAULT NULL COMMENT '课程id',
  `chapter_id` bigint NULL DEFAULT NULL COMMENT '章id',
  `section_id` bigint NULL DEFAULT NULL COMMENT '小节id',
  `note_moment` int NULL DEFAULT NULL COMMENT '记录笔记时的视频播放时间',
  `content` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '笔记内容',
  `is_private` bit(1) NULL DEFAULT b'0' COMMENT '是否是隐私笔记',
  `gathered_times` int NOT NULL DEFAULT 0 COMMENT '被采集次数',
  `liked_times` int NOT NULL DEFAULT 0 COMMENT '点赞数量',
  `hidden` bit(1) NULL DEFAULT b'0' COMMENT '是否被折叠（隐藏）',
  `hidden_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '被隐藏的原因',
  `author_id` bigint NULL DEFAULT NULL COMMENT '笔记作者id',
  `gathered_note_id` bigint NULL DEFAULT NULL COMMENT '被采集笔记的id',
  `is_gathered` bit(1) NULL DEFAULT b'0' COMMENT '是否是采集他人的笔记',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '笔记表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of note
-- ----------------------------
INSERT INTO `note` VALUES (1923959339245637634, 129, 3, 28, 29, 118, '666', b'0', 1, 888, b'0', NULL, 129, NULL, b'0', '2025-05-18 16:07:41', '2025-05-18 18:26:08');
INSERT INTO `note` VALUES (1923959458787495938, 129, 3, 28, 29, 118, '666', b'0', 1, 1, b'0', NULL, 129, NULL, b'0', '2025-05-18 16:07:38', '2025-05-18 17:19:20');
INSERT INTO `note` VALUES (1924011961700847617, 129, 3, 28, 30, 1, '66666', b'1', 0, 0, b'0', NULL, 129, NULL, b'0', '2025-05-18 15:59:38', '2025-05-18 16:08:43');
INSERT INTO `note` VALUES (1924012014762987521, 129, 3, 28, 30, 1, '555', b'1', 0, 0, b'0', NULL, 129, NULL, b'0', '2025-05-18 15:59:50', '2025-05-18 16:28:29');
INSERT INTO `note` VALUES (1924035825998872577, 2, 3, 28, 29, 118, '666', b'1', 0, 1, b'0', NULL, 129, 1923959458787495938, b'1', '2025-05-18 16:07:38', '2025-05-18 17:19:20');
INSERT INTO `note` VALUES (1924042824484507650, 2, 3, 28, 29, 116, '666', b'0', 0, 0, b'0', NULL, 2, NULL, b'0', '2025-05-18 18:02:16', '2025-05-18 18:02:16');
INSERT INTO `note` VALUES (1924043835185942529, 129, 1549025085494521857, 1550383240983875585, 1550383240983875586, 0, '5555', b'0', 1, 1, b'0', NULL, 129, NULL, b'0', '2025-05-18 18:06:17', '2025-05-22 16:18:29');
INSERT INTO `note` VALUES (1924044057240784897, 2, 3, 28, 29, 118, '666', b'1', 0, 1, b'0', NULL, 129, 1923959339245637634, b'1', '2025-05-18 16:07:41', '2025-05-18 17:34:41');
INSERT INTO `note` VALUES (1924289424200916993, 2, 1549025085494521857, 1550383240983875585, 1550383240983875586, 0, '5555', b'1', 0, 0, b'0', NULL, 129, 1924043835185942529, b'1', '2025-05-18 18:06:17', '2025-05-18 18:06:17');
INSERT INTO `note` VALUES (1925014348402008066, 2, 1549025085494521857, 1550383240983875585, 1550383240983875587, 126, '6666', b'0', 0, 0, b'0', NULL, 2, NULL, b'0', '2025-05-21 10:22:50', '2025-06-15 16:14:17');

-- ----------------------------
-- Table structure for points_board
-- ----------------------------
DROP TABLE IF EXISTS `points_board`;
CREATE TABLE `points_board`  (
  `id` bigint NOT NULL COMMENT '榜单id',
  `user_id` bigint NOT NULL COMMENT '学生id',
  `points` int NOT NULL COMMENT '积分值',
  `rank` tinyint NOT NULL COMMENT '名次，只记录赛季前100',
  `season` smallint NOT NULL COMMENT '赛季，例如 1,就是第一赛季，2-就是第二赛季',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_season_user`(`season` ASC, `user_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '学霸天梯榜' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of points_board
-- ----------------------------

-- ----------------------------
-- Table structure for points_board_season
-- ----------------------------
DROP TABLE IF EXISTS `points_board_season`;
CREATE TABLE `points_board_season`  (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '自增长id，season标示',
  `name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '赛季名称，例如：第1赛季',
  `begin_time` date NOT NULL COMMENT '赛季开始时间',
  `end_time` date NOT NULL COMMENT '赛季结束时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 15 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of points_board_season
-- ----------------------------
INSERT INTO `points_board_season` VALUES (1, '第1赛季', '2022-10-01', '2022-10-31');
INSERT INTO `points_board_season` VALUES (2, '第2赛季', '2022-11-01', '2022-11-30');
INSERT INTO `points_board_season` VALUES (3, '第3赛季', '2022-12-01', '2022-12-31');
INSERT INTO `points_board_season` VALUES (4, '第4赛季', '2023-01-01', '2023-01-31');
INSERT INTO `points_board_season` VALUES (5, '第5赛季', '2023-02-01', '2023-02-28');
INSERT INTO `points_board_season` VALUES (6, '第6赛季', '2023-03-01', '2023-03-31');
INSERT INTO `points_board_season` VALUES (7, '第7赛季', '2023-04-01', '2023-04-30');
INSERT INTO `points_board_season` VALUES (8, '第8赛季', '2023-05-01', '2023-05-31');
INSERT INTO `points_board_season` VALUES (9, '第9赛季', '2023-06-01', '2023-06-30');
INSERT INTO `points_board_season` VALUES (10, '第10赛季', '2023-07-01', '2023-07-31');
INSERT INTO `points_board_season` VALUES (11, '第11赛季', '2023-08-01', '2023-08-31');
INSERT INTO `points_board_season` VALUES (12, '第12赛季', '2023-09-01', '2023-09-30');
INSERT INTO `points_board_season` VALUES (13, '第13赛季', '2023-10-01', '2023-10-31');

-- ----------------------------
-- Table structure for points_boards_11
-- ----------------------------
DROP TABLE IF EXISTS `points_boards_11`;
CREATE TABLE `points_boards_11`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '榜单id',
  `user_id` bigint NOT NULL COMMENT '学生id',
  `points` int NOT NULL COMMENT '积分值',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '学霸天梯榜' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of points_boards_11
-- ----------------------------

-- ----------------------------
-- Table structure for points_boards_12
-- ----------------------------
DROP TABLE IF EXISTS `points_boards_12`;
CREATE TABLE `points_boards_12`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '榜单id',
  `user_id` bigint NOT NULL COMMENT '学生id',
  `points` int NOT NULL COMMENT '积分值',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '学霸天梯榜' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of points_boards_12
-- ----------------------------
INSERT INTO `points_boards_12` VALUES (1, 129, 32);
INSERT INTO `points_boards_12` VALUES (2, 2, 6);

-- ----------------------------
-- Table structure for points_exchange_records
-- ----------------------------
DROP TABLE IF EXISTS `points_exchange_records`;
CREATE TABLE `points_exchange_records`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '兑换记录ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `item_id` bigint NOT NULL COMMENT '商品ID',
  `points_used` int NOT NULL COMMENT '使用积分',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '兑换状态：0-待发货，1-已发货，2-已完成，3-已取消',
  `express_number` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '快递单号',
  `address` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '收货地址',
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '联系电话',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_item_id`(`item_id` ASC) USING BTREE,
  INDEX `idx_create_time`(`create_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '积分兑换记录表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of points_exchange_records
-- ----------------------------
INSERT INTO `points_exchange_records` VALUES (1, 2, 1, 66, 2, '88888888888888888888888', NULL, NULL, '2025-05-25 18:44:48', '2025-05-25 19:35:00');
INSERT INTO `points_exchange_records` VALUES (2, 2, 1, 2, 0, NULL, '时间222222', '18833387919', '2025-05-25 19:01:42', '2025-05-25 19:34:59');

-- ----------------------------
-- Table structure for points_mall_items
-- ----------------------------
DROP TABLE IF EXISTS `points_mall_items`;
CREATE TABLE `points_mall_items`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  `item_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商品名称',
  `item_desc` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '商品描述',
  `points_required` int NOT NULL COMMENT '所需积分',
  `stock` int NOT NULL DEFAULT 0 COMMENT '库存数量',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '商品状态：0-下架，1-上架',
  `image_url` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '商品图片URL',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_points_required`(`points_required` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '积分商城商品表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of points_mall_items
-- ----------------------------
INSERT INTO `points_mall_items` VALUES (1, '限定头像框', '6666', 2, 3, 1, 'https://gitee.com/widgets/gitee_tenth_large.png', '2025-05-25 18:43:19', '2025-05-25 19:01:44');

-- ----------------------------
-- Table structure for points_monthly_summary
-- ----------------------------
DROP TABLE IF EXISTS `points_monthly_summary`;
CREATE TABLE `points_monthly_summary`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `year_month` char(6) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '年月（YYYYMM）',
  `points_earned` int NOT NULL DEFAULT 0 COMMENT '当月获得积分',
  `points_used` int NOT NULL DEFAULT 0 COMMENT '当月使用积分',
  `points_expired` int NOT NULL DEFAULT 0 COMMENT '当月过期积分',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_user_month`(`user_id` ASC, `year_month` ASC) USING BTREE,
  INDEX `idx_year_month`(`year_month` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户月度积分汇总表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of points_monthly_summary
-- ----------------------------

-- ----------------------------
-- Table structure for points_record
-- ----------------------------
DROP TABLE IF EXISTS `points_record`;
CREATE TABLE `points_record`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '积分记录表id',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `type` tinyint NOT NULL COMMENT '积分方式：1-课程学习，2-每日签到，3-课程问答， 4-课程笔记，5-课程评价',
  `points` tinyint NOT NULL COMMENT '积分值',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC, `type` ASC) USING BTREE,
  INDEX `idx_create_time`(`create_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 90 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '学习积分记录，每个月底清零' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of points_record
-- ----------------------------
INSERT INTO `points_record` VALUES (41, 2, 2, 11, '2023-10-26 20:51:55');
INSERT INTO `points_record` VALUES (42, 2, 3, 3, '2023-10-26 21:02:03');
INSERT INTO `points_record` VALUES (43, 2, 3, 3, '2023-10-26 21:02:18');
INSERT INTO `points_record` VALUES (45, 2, 2, 1, '2023-10-27 16:25:33');
INSERT INTO `points_record` VALUES (46, 2, 2, 1, '2023-10-27 16:30:07');
INSERT INTO `points_record` VALUES (47, 2, 2, 1, '2023-10-27 16:31:52');
INSERT INTO `points_record` VALUES (48, 2, 2, 1, '2023-10-27 16:39:22');
INSERT INTO `points_record` VALUES (49, 2, 2, 1, '2023-10-27 16:40:36');
INSERT INTO `points_record` VALUES (50, 2, 2, 1, '2023-10-27 16:41:39');
INSERT INTO `points_record` VALUES (51, 2, 2, 1, '2023-10-27 16:44:08');
INSERT INTO `points_record` VALUES (52, 2, 2, 1, '2023-11-01 10:52:08');
INSERT INTO `points_record` VALUES (53, 2, 2, 1, '2023-11-07 19:16:56');
INSERT INTO `points_record` VALUES (54, 2, 2, 1, '2025-05-18 11:54:19');
INSERT INTO `points_record` VALUES (55, 2, 3, 5, '2025-05-18 18:00:53');
INSERT INTO `points_record` VALUES (56, 2, 4, 3, '2025-05-18 18:02:31');
INSERT INTO `points_record` VALUES (57, 2, 3, 5, '2025-05-18 18:02:58');
INSERT INTO `points_record` VALUES (58, 129, 4, 3, '2025-05-18 18:06:19');
INSERT INTO `points_record` VALUES (59, 129, 4, 2, '2025-05-18 18:07:10');
INSERT INTO `points_record` VALUES (60, 2, 2, 1, '2025-05-19 09:03:59');
INSERT INTO `points_record` VALUES (61, 129, 4, 2, '2025-05-19 10:22:12');
INSERT INTO `points_record` VALUES (62, 2, 2, 1, '2025-05-20 08:47:15');
INSERT INTO `points_record` VALUES (63, 2, 4, 3, '2025-05-21 10:22:50');
INSERT INTO `points_record` VALUES (64, 2, 2, 1, '2025-05-21 11:24:59');
INSERT INTO `points_record` VALUES (65, 2, 2, 1, '2025-05-22 10:28:26');
INSERT INTO `points_record` VALUES (66, 2, 4, 3, '2025-05-22 10:59:07');
INSERT INTO `points_record` VALUES (67, 2, 4, 10, '2025-05-22 19:18:28');
INSERT INTO `points_record` VALUES (68, 2, 5, 10, '2025-05-22 19:21:09');
INSERT INTO `points_record` VALUES (69, 2, 5, 10, '2025-05-22 19:22:38');
INSERT INTO `points_record` VALUES (70, 129, 5, 10, '2025-05-23 09:09:24');
INSERT INTO `points_record` VALUES (71, 2, 2, 1, '2025-05-23 12:28:03');
INSERT INTO `points_record` VALUES (72, 129, 2, 1, '2025-05-23 15:12:35');
INSERT INTO `points_record` VALUES (73, 2, 2, 1, '2025-05-25 09:42:20');
INSERT INTO `points_record` VALUES (74, 2, 2, 1, '2025-05-26 09:13:20');
INSERT INTO `points_record` VALUES (75, 2, 2, 1, '2025-05-29 09:26:14');
INSERT INTO `points_record` VALUES (76, 2, 2, 1, '2025-05-31 15:05:55');
INSERT INTO `points_record` VALUES (77, 2, 2, 1, '2025-06-01 10:56:50');
INSERT INTO `points_record` VALUES (78, 2, 3, 5, '2025-06-05 16:21:00');
INSERT INTO `points_record` VALUES (79, 2, 3, 5, '2025-06-05 17:08:14');
INSERT INTO `points_record` VALUES (80, 129, 2, 1, '2025-06-15 17:14:28');
INSERT INTO `points_record` VALUES (81, 2, 2, 1, '2025-06-16 18:02:26');
INSERT INTO `points_record` VALUES (82, 2, 5, 10, '2025-06-16 19:51:09');
INSERT INTO `points_record` VALUES (83, 5, 3, 5, '2025-07-01 19:39:14');
INSERT INTO `points_record` VALUES (84, 2, 2, 1, '2025-07-02 12:11:50');
INSERT INTO `points_record` VALUES (85, 2, 2, 1, '2025-08-08 22:48:14');
INSERT INTO `points_record` VALUES (86, 2, 2, 1, '2025-08-09 21:58:49');
INSERT INTO `points_record` VALUES (87, 2, 2, 1, '2025-08-10 15:38:51');
INSERT INTO `points_record` VALUES (88, 2, 5, 10, '2025-08-10 15:41:29');
INSERT INTO `points_record` VALUES (89, 5, 3, 5, '2025-08-17 22:24:26');

SET FOREIGN_KEY_CHECKS = 1;
