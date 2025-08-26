/*
 Navicat Premium Dump SQL

 Source Server         : tianji
 Source Server Type    : MySQL
 Source Server Version : 80029 (8.0.29)
 Source Host           : 192.168.150.101:3306
 Source Schema         : tj_message

 Target Server Type    : MySQL
 Target Server Version : 80029 (8.0.29)
 File Encoding         : 65001

 Date: 26/08/2025 22:04:01
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for chat_groups
-- ----------------------------
DROP TABLE IF EXISTS `chat_groups`;
CREATE TABLE `chat_groups`  (
  `id` bigint NOT NULL,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `tags` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `status` tinyint NULL DEFAULT 0,
  `creator_id` bigint NOT NULL,
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_creator`(`creator_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of chat_groups
-- ----------------------------
INSERT INTO `chat_groups` VALUES (0, '天机学堂用户交流群', 'http://localhost:18082/src/assets/logo-small.png', '官方', '天机学堂用户交流', 0, 1, '2025-05-28 16:51:14', '2025-05-28 16:51:14');
INSERT INTO `chat_groups` VALUES (1, '学习交流群', 'https://lf-flow-web-cdn.doubao.com/obj/flow-doubao/samantha/logo-icon-white-bg.png', '优秀', '很好的学习群', 0, 2, '2025-05-28 10:22:12', '2025-08-26 21:58:26');

-- ----------------------------
-- Table structure for chat_messages
-- ----------------------------
DROP TABLE IF EXISTS `chat_messages`;
CREATE TABLE `chat_messages`  (
  `id` bigint NOT NULL,
  `sender_id` bigint NOT NULL,
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `message_type` tinyint NOT NULL COMMENT '1:私聊 2:群聊',
  `target_id` bigint NOT NULL COMMENT '接收者ID或群ID',
  `sent_at` datetime(3) NOT NULL COMMENT '精确到毫秒',
  `status` tinyint NULL DEFAULT 1 COMMENT '1:已发送 2:已接收 3:已读',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_sender`(`sender_id` ASC) USING BTREE,
  INDEX `idx_target_type`(`target_id` ASC, `message_type` ASC) USING BTREE,
  INDEX `idx_sent`(`sent_at` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = COMPRESSED;

-- ----------------------------
-- Records of chat_messages
-- ----------------------------
INSERT INTO `chat_messages` VALUES (1927638401860300801, 129, '44', 1, 2, '2025-05-28 16:09:43.406', 1);
INSERT INTO `chat_messages` VALUES (1927647769007755266, 129, '大家好', 2, 0, '2025-05-28 16:46:56.744', 1);
INSERT INTO `chat_messages` VALUES (1927647829460258818, 2, '你好啊', 2, 0, '2025-05-28 16:47:11.174', 1);
INSERT INTO `chat_messages` VALUES (1927647902164324353, 129, '全体目光向我看齐！！！！', 2, 0, '2025-05-28 16:47:28.511', 1);
INSERT INTO `chat_messages` VALUES (1927647932539473922, 2, '2222', 2, 0, '2025-05-28 16:47:35.748', 1);
INSERT INTO `chat_messages` VALUES (1927653655285600257, 2, '你们好啊~', 2, 0, '2025-05-28 17:10:20.155', 1);
INSERT INTO `chat_messages` VALUES (1927701887499898882, 129, '22', 2, 0, '2025-05-28 20:21:59.606', 1);
INSERT INTO `chat_messages` VALUES (1927701920743952386, 2, '2025年5月28日20:22:05', 2, 0, '2025-05-28 20:22:07.538', 1);
INSERT INTO `chat_messages` VALUES (1927721584282832898, 2, '2025年5月28日22:08:27', 2, 0, '2025-05-28 21:40:15.697', 1);
INSERT INTO `chat_messages` VALUES (1927726086780096513, 2, '5', 2, 0, '2025-05-28 21:58:09.171', 1);
INSERT INTO `chat_messages` VALUES (1927726201448173569, 2, '33333', 2, 0, '2025-05-28 21:58:36.509', 1);
INSERT INTO `chat_messages` VALUES (1927726299095764994, 2, '你们好', 2, 0, '2025-05-28 21:58:59.797', 1);
INSERT INTO `chat_messages` VALUES (1927732332971167746, 2, '22', 1, 1924703349186879490, '2025-05-28 22:22:58.378', 1);
INSERT INTO `chat_messages` VALUES (1927744629458341890, 2, '333', 2, 1, '2025-05-28 23:11:50.091', 1);
INSERT INTO `chat_messages` VALUES (1932675588700094465, 1924716682464063490, '你们好吖', 2, 0, '2025-06-11 13:45:42.376', 1);
INSERT INTO `chat_messages` VALUES (1933850253229334530, 2, '33', 2, 0, '2025-06-14 19:33:24.218', 1);
INSERT INTO `chat_messages` VALUES (1938117263635271681, 1924716682464063490, 'hi?', 1, 2, '2025-06-26 14:08:58.769', 1);
INSERT INTO `chat_messages` VALUES (1938117347873673218, 2, 'hello', 2, 0, '2025-06-26 14:09:18.880', 1);
INSERT INTO `chat_messages` VALUES (1938117378404012033, 1924716682464063490, '777', 2, 0, '2025-06-26 14:09:26.154', 1);
INSERT INTO `chat_messages` VALUES (1954445742651826177, 2, 'hello~', 1, 129, '2025-08-10 15:32:31.526', 1);
INSERT INTO `chat_messages` VALUES (1954447220376428545, 129, 'hi~', 1, 2, '2025-08-10 15:38:23.871', 1);
INSERT INTO `chat_messages` VALUES (1954447281416134657, 2, 'hello~', 2, 0, '2025-08-10 15:38:38.435', 1);
INSERT INTO `chat_messages` VALUES (1954457077905186817, 2, 'hello~~~~', 1, 129, '2025-08-10 16:17:34.098', 1);
INSERT INTO `chat_messages` VALUES (1954457314606538754, 129, 'hi！！', 2, 0, '2025-08-10 16:18:30.529', 1);

-- ----------------------------
-- Table structure for group_members
-- ----------------------------
DROP TABLE IF EXISTS `group_members`;
CREATE TABLE `group_members`  (
  `id` bigint NOT NULL,
  `group_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `joined_at` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_group_user`(`group_id` ASC, `user_id` ASC) USING BTREE,
  INDEX `idx_user`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of group_members
-- ----------------------------
INSERT INTO `group_members` VALUES (1927628141183373314, 1, 129, '2025-05-28 15:28:57');
INSERT INTO `group_members` VALUES (1927700929923518466, 0, 2, '2025-05-28 20:18:11');
INSERT INTO `group_members` VALUES (1932675490993782786, 0, 1924716682464063490, '2025-06-11 13:45:19');

-- ----------------------------
-- Table structure for message_receipts
-- ----------------------------
DROP TABLE IF EXISTS `message_receipts`;
CREATE TABLE `message_receipts`  (
  `id` bigint NOT NULL,
  `message_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `status` tinyint NULL DEFAULT 1 COMMENT '1:已发送 2:已接收 3:已读',
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_message_user`(`message_id` ASC, `user_id` ASC) USING BTREE,
  INDEX `idx_user_status`(`user_id` ASC, `status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of message_receipts
-- ----------------------------

-- ----------------------------
-- Table structure for message_template
-- ----------------------------
DROP TABLE IF EXISTS `message_template`;
CREATE TABLE `message_template`  (
  `id` bigint NOT NULL COMMENT '短信发送模板id',
  `name` varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '模板名称',
  `platform_code` varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '第三方短信平台代号',
  `sign_name` varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '签名',
  `third_template_code` varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '第三方短信模板code',
  `content` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '第三方短信模板内容预览',
  `template_id` bigint NOT NULL COMMENT '通知模板id',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '模板状态:  0-禁用，1-启用',
  `creater` bigint NOT NULL COMMENT '创建者',
  `updater` bigint NOT NULL COMMENT '更新者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_template_id`(`template_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci COMMENT = '第三方短信平台签名和模板信息' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of message_template
-- ----------------------------
INSERT INTO `message_template` VALUES (1561895814765240322, '短信验证码', 'aliYun', '瑞吉外卖', 'SMS_460795554', '验证码:{code},您在使用天机学堂短信验证功能，仅限本人使用，请勿向他人泄露验证码信息', 1561895814668771330, 0, 0, 0, '2022-08-23 01:59:16', '2025-05-20 13:16:01');
INSERT INTO `message_template` VALUES (1561897355307925505, '短信验证码', 'tencent', '天机学堂', 'SMS_185822702', '您的验证码为xxx,验证码5分钟内有效，请勿泄露给他人！', 1561895814668771330, 0, 0, 0, '2022-08-23 02:05:24', '2022-08-23 02:05:24');
INSERT INTO `message_template` VALUES (1930808947590656002, '系统公告', '66', '6666', '666', '系统公告测试', 1930808947531935745, 1, 1, 1, '2025-06-06 10:08:22', '2025-06-06 16:48:01');
INSERT INTO `message_template` VALUES (1930829718375387138, '非短信模板', 'qiniu', '222222', '22222', '222', 1930829539794505729, 0, 1, 1, '2025-06-06 11:30:54', '2025-06-06 11:35:03');

-- ----------------------------
-- Table structure for notice_task
-- ----------------------------
DROP TABLE IF EXISTS `notice_task`;
CREATE TABLE `notice_task`  (
  `id` bigint NOT NULL COMMENT '公告任务id',
  `template_id` bigint NOT NULL COMMENT '任务对应的通知模板id',
  `name` varchar(128) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '任务名称',
  `partial` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否是部分人的通告，默认false',
  `push_time` datetime NULL DEFAULT NULL COMMENT '任务预期执行时间',
  `interval` int NULL DEFAULT NULL COMMENT '任务延迟执行时间间隔，单位是分钟',
  `expire_time` datetime NULL DEFAULT NULL COMMENT '任务失效时间',
  `max_times` int NULL DEFAULT 1 COMMENT '任务重复执行次数上限，1则只发一次',
  `finished` bit(1) NULL DEFAULT b'0' COMMENT '任务是否完成，默认false',
  `creater` bigint NOT NULL COMMENT '创建人',
  `updater` bigint NOT NULL COMMENT '更新人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci COMMENT = '系统通告的任务表，可以延期或定期发送通告' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of notice_task
-- ----------------------------
INSERT INTO `notice_task` VALUES (1, 1930808947531935745, '短信发送任务66', b'1', '2025-06-02 23:45:43', 60, '2025-12-03 19:45:53', 0, b'1', 2, 2, '2025-06-03 19:46:07', '2025-06-06 16:59:42');
INSERT INTO `notice_task` VALUES (1932352502880579585, 1930808947531935745, '公告全体测试', b'0', '2025-06-10 16:21:51', 1, '2025-06-24 23:59:59', 0, b'1', 1, 1, '2025-06-10 16:21:55', '2025-06-10 16:21:59');

-- ----------------------------
-- Table structure for notice_task_target
-- ----------------------------
DROP TABLE IF EXISTS `notice_task_target`;
CREATE TABLE `notice_task_target`  (
  `task_id` bigint NOT NULL COMMENT '任务id',
  `target_id` bigint NOT NULL COMMENT '目标用户id',
  PRIMARY KEY (`task_id`, `target_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '通知任务的目标用户信息' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of notice_task_target
-- ----------------------------
INSERT INTO `notice_task_target` VALUES (1, 2);
INSERT INTO `notice_task_target` VALUES (1, 129);

-- ----------------------------
-- Table structure for notice_template
-- ----------------------------
DROP TABLE IF EXISTS `notice_template`;
CREATE TABLE `notice_template`  (
  `id` bigint NOT NULL COMMENT '通知模板id',
  `name` varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '通知模板名称',
  `code` varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '模板代号，例如：verify-code',
  `type` tinyint NOT NULL COMMENT '通知类型：0-系统通知，1-笔记通知，2-问答通知，3-其它通知',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '模板状态:  0-草稿，1-使用中，2-停用',
  `title` varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '通知标题，短信模板可以不填',
  `content` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '通知内容模板',
  `is_sms_template` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否是短信模板，默认false',
  `creater` bigint NOT NULL COMMENT '创建人',
  `updater` bigint NOT NULL COMMENT '更新人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_code`(`code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci COMMENT = '通知模板' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of notice_template
-- ----------------------------
INSERT INTO `notice_template` VALUES (1561895814668771330, '短信验证码', 'VERIFY_CODE', 3, 1, '——短信验证码——', '您的验证码为xxx,验证码5分钟内有效，请勿泄露给他人！', b'1', 0, 0, '2022-08-23 01:59:16', '2025-06-05 22:02:02');
INSERT INTO `notice_template` VALUES (1930808947531935745, '系统公告', 'Public_Notice', 0, 1, '系统公告', '系统公告测试', b'0', 1, 1, '2025-06-06 10:08:22', '2025-06-06 16:48:01');
INSERT INTO `notice_template` VALUES (1930829539794505729, '非短信模板', '122', 1, 1, '222', '222', b'0', 1, 1, '2025-06-06 11:30:12', '2025-06-06 11:30:12');

-- ----------------------------
-- Table structure for public_notice
-- ----------------------------
DROP TABLE IF EXISTS `public_notice`;
CREATE TABLE `public_notice`  (
  `id` bigint NOT NULL COMMENT '公告id',
  `title` varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '公告标题',
  `content` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '公告通知内容，可以存放公告消息模板',
  `type` tinyint NOT NULL COMMENT '通知类型：0-系统通知，1-笔记通知，2-问答通知，3-其它通知',
  `push_time` datetime NOT NULL COMMENT '公告预期发送时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '通知发布时间',
  `expire_time` datetime NOT NULL COMMENT '通知失效时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci COMMENT = '公告消息模板' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of public_notice
-- ----------------------------
INSERT INTO `public_notice` VALUES (1932352521331322882, '系统公告', '系统公告测试', 0, '2025-06-10 16:21:57', '2025-06-10 18:45:06', '2025-07-10 16:21:57');

-- ----------------------------
-- Table structure for sensitive
-- ----------------------------
DROP TABLE IF EXISTS `sensitive`;
CREATE TABLE `sensitive`  (
  `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sensitives` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '敏感词',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1933865522844696588 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '敏感词信息表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sensitive
-- ----------------------------
INSERT INTO `sensitive` VALUES (1, '666', '2025-06-14 18:58:37');
INSERT INTO `sensitive` VALUES (3, '114514', '2025-06-14 18:58:53');
INSERT INTO `sensitive` VALUES (1933865522844696579, '只因你太美', '2025-06-15 08:45:45');
INSERT INTO `sensitive` VALUES (1933865522844696580, '杰哥不要', '2025-06-15 08:45:50');
INSERT INTO `sensitive` VALUES (1933865522844696581, '坤', '2025-06-15 08:46:01');
INSERT INTO `sensitive` VALUES (1933865522844696582, '两年半', '2025-06-15 08:46:07');
INSERT INTO `sensitive` VALUES (1933865522844696583, '电子烟', '2025-06-15 08:46:12');
INSERT INTO `sensitive` VALUES (1933865522844696584, '鸡你实在是太美', '2025-06-15 08:46:22');
INSERT INTO `sensitive` VALUES (1933865522844696585, '鸡你太美', '2025-06-15 08:46:29');
INSERT INTO `sensitive` VALUES (1933865522844696586, '姬霓太美', '2025-06-15 08:46:34');
INSERT INTO `sensitive` VALUES (1933865522844696587, '刻章办', '2025-06-15 08:46:46');

-- ----------------------------
-- Table structure for sms_third_platform
-- ----------------------------
DROP TABLE IF EXISTS `sms_third_platform`;
CREATE TABLE `sms_third_platform`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '短信平台id',
  `name` varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '短信平台名称',
  `code` varchar(50) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '短信平台代码，例如：ali',
  `priority` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '数字越小优先级越高，最小为0',
  `status` int NOT NULL DEFAULT 1 COMMENT '短信平台状态：0-禁用，1-启用',
  `creater` bigint NOT NULL COMMENT '创建人',
  `updater` bigint NOT NULL COMMENT '更新人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 6 CHARACTER SET = utf8 COLLATE = utf8_general_ci COMMENT = '第三方云通讯平台' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sms_third_platform
-- ----------------------------
INSERT INTO `sms_third_platform` VALUES (1, '阿里云', 'aliYun', 0, 1, 0, 0, '2022-08-23 01:04:57', '2022-08-23 01:04:57');
INSERT INTO `sms_third_platform` VALUES (2, '腾讯云', 'tencent', 1, 1, 0, 0, '2022-08-23 01:05:18', '2022-08-23 01:05:18');
INSERT INTO `sms_third_platform` VALUES (3, '优刻得', 'uCloud', 3, 1, 0, 0, '2022-08-23 01:06:14', '2025-06-06 11:32:39');
INSERT INTO `sms_third_platform` VALUES (5, '七牛云', 'qiniu', 0, 0, 1, 1, '2025-06-06 11:39:53', '2025-06-06 11:39:53');

-- ----------------------------
-- Table structure for user_conversation
-- ----------------------------
DROP TABLE IF EXISTS `user_conversation`;
CREATE TABLE `user_conversation`  (
  `id` bigint UNSIGNED NOT NULL COMMENT '会话ID',
  `user_id_1` bigint UNSIGNED NOT NULL COMMENT '参与用户1',
  `user_id_2` bigint UNSIGNED NOT NULL COMMENT '参与用户2',
  `last_message_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '最后一条消息ID',
  `unread_count_1` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '用户1的未读数量',
  `unread_count_2` int UNSIGNED NOT NULL DEFAULT 0 COMMENT '用户2的未读数量',
  `last_update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  `status` tinyint(1) NOT NULL DEFAULT 0 COMMENT '会话状态（0=正常，1=已屏蔽）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uniq_users`(`user_id_1` ASC, `user_id_2` ASC) USING BTREE,
  INDEX `idx_user1`(`user_id_1` ASC, `last_update_time` ASC) USING BTREE,
  INDEX `idx_user2`(`user_id_2` ASC, `last_update_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户会话表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user_conversation
-- ----------------------------
INSERT INTO `user_conversation` VALUES (1, 2, 129, 66, 2, 22, '2025-08-08 22:45:24', 0);
INSERT INTO `user_conversation` VALUES (2, 1548889371405492225, 2, 1, 0, 0, '2025-05-24 16:49:03', 0);
INSERT INTO `user_conversation` VALUES (4, 2, 1924703349186879490, 2, 0, 0, '2025-05-26 11:12:28', 0);
INSERT INTO `user_conversation` VALUES (5, 1, 2, NULL, 0, 0, '2025-05-24 16:40:33', 0);
INSERT INTO `user_conversation` VALUES (6, 5, 2, NULL, 0, 0, '2025-05-26 11:12:26', 0);
INSERT INTO `user_conversation` VALUES (7, 1628197517835370497, 2, 1, 0, 0, '2025-05-26 11:11:46', 0);
INSERT INTO `user_conversation` VALUES (10, 2, 1628197519030747137, NULL, 0, 0, '2025-05-24 18:12:44', 0);
INSERT INTO `user_conversation` VALUES (11, 1628197518737145858, 2, NULL, 0, 0, '2025-05-24 18:07:37', 0);
INSERT INTO `user_conversation` VALUES (12, 2, 1628197518124777473, NULL, 0, 0, '2025-05-26 09:13:11', 0);
INSERT INTO `user_conversation` VALUES (13, 2, 1548940921662365698, NULL, 0, 0, '2025-05-26 11:13:31', 0);

-- ----------------------------
-- Table structure for user_inbox
-- ----------------------------
DROP TABLE IF EXISTS `user_inbox`;
CREATE TABLE `user_inbox`  (
  `id` bigint NOT NULL COMMENT '用户通知id',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `notice_id` bigint NULL DEFAULT NULL COMMENT '公告id，如果是系统通知，则会存入公告id，防止重发',
  `type` tinyint NULL DEFAULT 4 COMMENT '通知类型：0-系统通知，1-笔记通知，2-问答通知，3-其它通知',
  `title` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT '' COMMENT '通知标题',
  `content` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '通知内容',
  `is_read` bit(1) NOT NULL DEFAULT b'0' COMMENT '公告是否已读',
  `publisher` bigint NOT NULL DEFAULT 0 COMMENT '通知的发送者id，0则代表是系统',
  `push_time` datetime NOT NULL COMMENT '创建时间',
  `expire_time` datetime NOT NULL COMMENT '过期时间，一旦过期用户端不在展示',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `user_id`(`user_id` ASC) USING BTREE,
  INDEX `push_time`(`push_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci COMMENT = '用户通知记录' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user_inbox
-- ----------------------------
INSERT INTO `user_inbox` VALUES (1930908583278006274, 2, NULL, 3, '——短信验证码——', '您的验证码为xxx,验证码5分钟内有效，请勿泄露给他人！', b'1', 0, '2025-06-06 16:44:15', '2025-12-06 16:44:15');
INSERT INTO `user_inbox` VALUES (1930908583340920833, 129, NULL, 3, '——短信验证码——', '您的验证码为xxx,验证码5分钟内有效，请勿泄露给他人！', b'0', 0, '2025-06-06 16:44:15', '2025-12-06 16:44:15');
INSERT INTO `user_inbox` VALUES (1930911612861497345, 2, NULL, 0, '系统公告', '系统公告测试', b'1', 0, '2025-06-06 16:56:18', '2025-12-06 16:56:18');
INSERT INTO `user_inbox` VALUES (1930911612874080258, 129, NULL, 0, '系统公告', '系统公告测试', b'0', 0, '2025-06-06 16:56:18', '2025-12-06 16:56:18');
INSERT INTO `user_inbox` VALUES (1930912472068866050, 2, NULL, 0, '系统公告', '系统公告测试', b'1', 0, '2025-06-06 16:59:43', '2025-12-06 16:59:43');
INSERT INTO `user_inbox` VALUES (1930912472115003393, 129, NULL, 0, '系统公告', '系统公告测试', b'0', 0, '2025-06-06 16:59:43', '2025-12-06 16:59:43');
INSERT INTO `user_inbox` VALUES (1932388557230989314, 2, NULL, 0, '系统公告', '系统公告测试', b'1', 0, '2025-06-10 16:21:57', '2025-07-10 16:21:57');
INSERT INTO `user_inbox` VALUES (1932388609504600065, 2, NULL, 0, '系统公告', '系统公告测试', b'1', 0, '2025-06-10 16:21:57', '2025-07-10 16:21:57');
INSERT INTO `user_inbox` VALUES (1932388626223095809, 2, NULL, 0, '系统公告', '系统公告测试', b'0', 0, '2025-06-10 16:21:57', '2025-07-10 16:21:57');
INSERT INTO `user_inbox` VALUES (1932390498086436865, 2, 1932352521331322882, 0, '系统公告', '系统公告测试', b'1', 0, '2025-06-10 16:21:57', '2025-07-10 16:21:57');
INSERT INTO `user_inbox` VALUES (1932390929084727298, 129, 1932352521331322882, 0, '系统公告', '系统公告测试', b'0', 0, '2025-06-10 16:21:57', '2025-07-10 16:21:57');
INSERT INTO `user_inbox` VALUES (1932675633558175746, 1924716682464063490, 1932352521331322882, 0, '系统公告', '系统公告测试', b'0', 0, '2025-06-10 16:21:57', '2025-07-10 16:21:57');
INSERT INTO `user_inbox` VALUES (1932677968669818881, 1924703349186879490, 1932352521331322882, 0, '系统公告', '系统公告测试', b'0', 0, '2025-06-10 16:21:57', '2025-07-10 16:21:57');
INSERT INTO `user_inbox` VALUES (1933828208944865282, 1933828031857049602, 1932352521331322882, 0, '系统公告', '系统公告测试', b'0', 0, '2025-06-10 16:21:57', '2025-07-10 16:21:57');

-- ----------------------------
-- Table structure for user_private_message
-- ----------------------------
DROP TABLE IF EXISTS `user_private_message`;
CREATE TABLE `user_private_message`  (
  `id` bigint UNSIGNED NOT NULL COMMENT '消息ID',
  `sender_id` bigint UNSIGNED NOT NULL COMMENT '发送者ID',
  `receiver_id` bigint UNSIGNED NOT NULL COMMENT '接收者ID',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '消息内容',
  `message_type` tinyint(1) NOT NULL DEFAULT 0 COMMENT '消息类型（0=文本，1=图片，2=语音，3=文件）',
  `is_read` tinyint(1) NOT NULL DEFAULT 0 COMMENT '已读状态（0=未读，1=已读）',
  `push_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  `status` tinyint(1) NOT NULL DEFAULT 0 COMMENT '消息状态（0=正常消息，1=置顶消息）',
  `delete_flag` tinyint(1) NOT NULL DEFAULT 0 COMMENT '删除标记（0=未删除，1=已删除）',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_sender_receiver`(`sender_id` ASC, `receiver_id` ASC, `delete_flag` ASC) USING BTREE,
  INDEX `idx_session_time`(`push_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户私信表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user_private_message
-- ----------------------------
INSERT INTO `user_private_message` VALUES (1, 2, 129, '1', 0, 1, '2025-05-21 19:58:19', 0, 0);
INSERT INTO `user_private_message` VALUES (2, 129, 2, '2', 0, 1, '2025-05-23 18:36:15', 0, 0);
INSERT INTO `user_private_message` VALUES (3, 129, 2, '3', 0, 1, '2025-05-23 18:55:33', 0, 0);
INSERT INTO `user_private_message` VALUES (4, 2, 129, '4', 0, 1, '2025-05-23 19:16:40', 0, 0);
INSERT INTO `user_private_message` VALUES (5, 2, 129, '5', 0, 1, '2025-05-23 19:20:13', 0, 0);
INSERT INTO `user_private_message` VALUES (6, 2, 129, '6', 0, 1, '2025-05-23 19:23:06', 0, 0);
INSERT INTO `user_private_message` VALUES (7, 2, 129, '7', 0, 1, '2025-05-23 19:23:09', 0, 0);
INSERT INTO `user_private_message` VALUES (8, 2, 129, '8', 0, 1, '2025-05-23 19:43:47', 0, 0);
INSERT INTO `user_private_message` VALUES (9, 2, 129, '9', 0, 1, '2025-05-23 19:46:36', 0, 0);
INSERT INTO `user_private_message` VALUES (10, 2, 129, '10', 0, 1, '2025-05-23 19:48:00', 0, 0);
INSERT INTO `user_private_message` VALUES (1926124410809876481, 2, 1628197519030747137, '666', 0, 1, '2025-05-24 11:53:41', 0, 0);
INSERT INTO `user_private_message` VALUES (1926126661083332610, 2, 129, '11', 0, 1, '2025-05-24 12:02:38', 0, 0);
INSERT INTO `user_private_message` VALUES (1926126679945117697, 2, 129, '12', 0, 1, '2025-05-24 12:02:42', 0, 0);
INSERT INTO `user_private_message` VALUES (1926126736299786242, 2, 129, '13', 0, 1, '2025-05-24 12:02:56', 0, 0);
INSERT INTO `user_private_message` VALUES (1926126771196395522, 2, 1924703349186879490, '你好，约吗？2025年5月24日14:06:36在天台等你哦', 0, 1, '2025-05-24 12:03:04', 0, 0);
INSERT INTO `user_private_message` VALUES (1926132396454313986, 2, 129, '14', 0, 1, '2025-05-24 12:25:25', 0, 0);
INSERT INTO `user_private_message` VALUES (1926132435562004482, 2, 129, '15', 0, 1, '2025-05-24 12:25:34', 0, 0);
INSERT INTO `user_private_message` VALUES (1926153843214131202, 129, 2, '16', 0, 1, '2025-05-24 13:50:38', 0, 0);
INSERT INTO `user_private_message` VALUES (1926155370704265217, 2, 129, '17', 0, 1, '2025-05-24 13:56:43', 0, 0);
INSERT INTO `user_private_message` VALUES (1926155776431874049, 2, 129, '18', 0, 1, '2025-05-24 13:58:19', 0, 0);
INSERT INTO `user_private_message` VALUES (1926155788884762625, 2, 129, '19', 0, 1, '2025-05-24 13:58:22', 0, 0);
INSERT INTO `user_private_message` VALUES (1926156010964770817, 2, 129, '20', 0, 1, '2025-05-24 13:59:15', 0, 0);
INSERT INTO `user_private_message` VALUES (1926156222932312066, 2, 129, '21', 0, 1, '2025-05-24 14:00:06', 0, 0);
INSERT INTO `user_private_message` VALUES (1926158333808074754, 2, 129, '22', 0, 1, '2025-05-24 14:08:29', 0, 0);
INSERT INTO `user_private_message` VALUES (1926158535856087042, 2, 1924703349186879490, '22', 0, 1, '2025-05-24 14:09:17', 0, 0);
INSERT INTO `user_private_message` VALUES (1926158597394915330, 2, 129, '23', 0, 1, '2025-05-24 14:09:32', 0, 0);
INSERT INTO `user_private_message` VALUES (1926159110752559106, 2, 129, '24', 0, 1, '2025-05-24 14:11:34', 0, 0);
INSERT INTO `user_private_message` VALUES (1926159847465279489, 2, 129, '25', 0, 1, '2025-05-24 14:14:30', 0, 0);
INSERT INTO `user_private_message` VALUES (1926162157163634689, 2, 129, '26', 0, 1, '2025-05-24 14:23:41', 0, 0);
INSERT INTO `user_private_message` VALUES (1926162741333712897, 2, 129, '27', 0, 1, '2025-05-24 14:26:00', 0, 0);
INSERT INTO `user_private_message` VALUES (1926192168180510721, 2, 1628197518737145858, '444', 0, 1, '2025-05-24 16:22:56', 0, 0);
INSERT INTO `user_private_message` VALUES (1926192188954898433, 2, 1628197518124777473, '6666', 0, 1, '2025-05-24 16:23:01', 0, 0);
INSERT INTO `user_private_message` VALUES (1926192222010208258, 2, 1628197519345319938, '77', 0, 1, '2025-05-24 16:23:08', 0, 0);
INSERT INTO `user_private_message` VALUES (1926192517658210305, 2, 1628197518737145858, '好', 0, 1, '2025-05-24 16:24:19', 0, 0);
INSERT INTO `user_private_message` VALUES (1926198969915437058, 2, 5, '很好', 0, 1, '2025-05-24 16:49:57', 0, 0);
INSERT INTO `user_private_message` VALUES (1926198988739473410, 2, 1548940676303970306, '22', 0, 1, '2025-05-24 16:50:02', 0, 0);
INSERT INTO `user_private_message` VALUES (1926809550020259842, 129, 2, '28', 0, 1, '2025-05-26 09:16:14', 0, 0);
INSERT INTO `user_private_message` VALUES (1926809562657697794, 129, 2, '29', 0, 1, '2025-05-26 09:16:17', 0, 0);
INSERT INTO `user_private_message` VALUES (1926809676545634305, 129, 2, '30', 0, 1, '2025-05-26 09:16:44', 0, 0);
INSERT INTO `user_private_message` VALUES (1926832029719695362, 2, 129, '2', 0, 1, '2025-05-26 10:45:34', 0, 0);
INSERT INTO `user_private_message` VALUES (1926837723969720321, 2, 1628197518737145858, '666', 0, 1, '2025-05-26 11:08:11', 0, 0);
INSERT INTO `user_private_message` VALUES (1926837897207058434, 2, 1548940921662365698, '333', 0, 1, '2025-05-26 11:08:53', 0, 0);
INSERT INTO `user_private_message` VALUES (1926837967394541570, 2, 1924703349186879490, '3', 0, 1, '2025-05-26 11:09:09', 0, 0);
INSERT INTO `user_private_message` VALUES (1926838153734885378, 2, 1628197518124777473, '44', 0, 1, '2025-05-26 11:09:54', 0, 0);
INSERT INTO `user_private_message` VALUES (1926838181899636737, 2, 1628197519030747137, '3', 0, 1, '2025-05-26 11:10:01', 0, 0);
INSERT INTO `user_private_message` VALUES (1926838222819266561, 2, 1, '3', 0, 1, '2025-05-26 11:10:10', 0, 0);
INSERT INTO `user_private_message` VALUES (1926838459948437505, 2, 1628197518737145858, '3', 0, 1, '2025-05-26 11:11:07', 0, 0);
INSERT INTO `user_private_message` VALUES (1926838470438391810, 2, 1628197518737145858, '2', 0, 1, '2025-05-26 11:11:09', 0, 0);
INSERT INTO `user_private_message` VALUES (1926838478172688386, 2, 1628197517835370497, '3', 0, 1, '2025-05-26 11:11:11', 0, 0);
INSERT INTO `user_private_message` VALUES (1932390956301565953, 129, 2, '你好啊', 0, 1, '2025-06-10 18:54:41', 0, 0);
INSERT INTO `user_private_message` VALUES (1932391210161815553, 2, 129, '你好\n', 0, 1, '2025-06-10 18:55:41', 0, 0);
INSERT INTO `user_private_message` VALUES (1932391346074042369, 129, 2, '666', 0, 1, '2025-06-10 18:56:14', 0, 0);
INSERT INTO `user_private_message` VALUES (1932393651808800770, 129, 2, '你好\n', 0, 1, '2025-06-10 19:05:23', 0, 0);
INSERT INTO `user_private_message` VALUES (1932393759128457217, 2, 129, 'hello\n\n', 0, 1, '2025-06-10 19:05:49', 0, 0);
INSERT INTO `user_private_message` VALUES (1932395691800862722, 2, 129, 'hi\n', 0, 1, '2025-06-10 19:13:30', 0, 0);
INSERT INTO `user_private_message` VALUES (1932395720141774850, 129, 2, '666', 0, 1, '2025-06-10 19:13:37', 0, 0);
INSERT INTO `user_private_message` VALUES (1932398763461632001, 129, 2, '你好\n', 0, 1, '2025-06-10 19:25:44', 0, 0);
INSERT INTO `user_private_message` VALUES (1932398786983288834, 2, 129, '你好~', 0, 1, '2025-06-10 19:25:49', 0, 0);
INSERT INTO `user_private_message` VALUES (1932399147974451202, 129, 2, '6666', 0, 1, '2025-06-10 19:27:16', 0, 0);
INSERT INTO `user_private_message` VALUES (1932399728550010882, 129, 2, '666', 0, 1, '2025-06-10 19:29:34', 0, 0);
INSERT INTO `user_private_message` VALUES (1932399980787085313, 2, 129, '749', 0, 1, '2025-06-10 19:30:34', 0, 0);
INSERT INTO `user_private_message` VALUES (1953838840645521409, 2, 5, '44', 0, 0, '2025-08-08 23:20:56', 0, 0);
INSERT INTO `user_private_message` VALUES (1953839281877913601, 2, 1548940921662365698, '6', 0, 0, '2025-08-08 23:22:41', 0, 0);
INSERT INTO `user_private_message` VALUES (1953839599202177026, 2, 5, '222', 0, 0, '2025-08-08 23:23:57', 0, 0);
INSERT INTO `user_private_message` VALUES (1953839845680451585, 2, 5, '55', 0, 0, '2025-08-08 23:24:56', 0, 0);
INSERT INTO `user_private_message` VALUES (1953839985619210242, 2, 1924703349186879490, '4', 0, 0, '2025-08-08 23:25:29', 0, 0);

SET FOREIGN_KEY_CHECKS = 1;
