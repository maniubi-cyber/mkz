/*
 Navicat Premium Dump SQL

 Source Server         : tianji
 Source Server Type    : MySQL
 Source Server Version : 80029 (8.0.29)
 Source Host           : 192.168.150.101:3306
 Source Schema         : tj_chat

 Target Server Type    : MySQL
 Target Server Version : 80029 (8.0.29)
 File Encoding         : 65001

 Date: 26/08/2025 22:03:22
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for chat_session
-- ----------------------------
DROP TABLE IF EXISTS `chat_session`;
CREATE TABLE `chat_session`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `session_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '会话ID',
  `segment_index` int NOT NULL COMMENT '会话片段序号，从0开始',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '消息内容，JSON格式，包含role、type、text等',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '插入时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_session`(`user_id` ASC, `session_id` ASC) USING BTREE COMMENT '用户和会话的联合索引'
) ENGINE = InnoDB AUTO_INCREMENT = 1954457995354574851 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '聊天对话的每个片段记录（分片存储）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of chat_session
-- ----------------------------
INSERT INTO `chat_session` VALUES (1954445586271309827, 2, '238d00c8-7b0a-45cc-9721-88a370264da8', 28, '{\"text\":\"\\u003cthink\\u003e\\n好的，用户发来的是“你好”，我需要先回应他的问候。根据提供的工具，用户可能希望我执行某个功能，但当前的消息看起来像是一个简单的问候。检查可用的工具，sum、queryCourse和squareRoot都是数学相关的函数，但用户并没有提供具体的数字或课程名称。因此，我应该直接回应问候，确认用户是否需要帮助，并询问具体需求。确保不使用任何工具，直接进行友好回应。\\n\\u003c/think\\u003e\\n\\n你好！有什么可以帮助你的吗？😊\",\"type\":\"AI\"}', '2025-08-10 15:31:54');
INSERT INTO `chat_session` VALUES (1954445614276677634, 2, '238d00c8-7b0a-45cc-9721-88a370264da8', 29, '{\"contents\":[{\"text\":\"hello~\",\"type\":\"TEXT\"}],\"type\":\"USER\"}', '2025-08-10 15:32:01');
INSERT INTO `chat_session` VALUES (1954445614276677635, 2, '238d00c8-7b0a-45cc-9721-88a370264da8', 30, '{\"text\":\"\\u003cthink\\u003e\\nOkay, the user said \\\"hello~\\\". I need to respond appropriately. Since there are no specific functions needed here, like summing numbers or querying a course, I just greet them and ask how I can help. No tool calls are necessary. Let me make sure there\\u0027s no hidden request. Yep, just a greeting. So I\\u0027ll say hello and offer assistance.\\n\\u003c/think\\u003e\\n\\nHello! How can I assist you today? 😊\",\"type\":\"AI\"}', '2025-08-10 15:32:01');
INSERT INTO `chat_session` VALUES (1954447592268500993, 2, '277e176c-fb89-4038-91f2-e5f1a017bc4f', 102, '{\"contents\":[{\"text\":\"你好~\",\"type\":\"TEXT\"}],\"type\":\"USER\"}', '2025-08-10 15:39:53');
INSERT INTO `chat_session` VALUES (1954447592268500994, 2, '277e176c-fb89-4038-91f2-e5f1a017bc4f', 103, '{\"text\":\"\\u003cthink\\u003e\\n好的，用户发来“你好~”，看起来是初次对话。我需要先回应他们的问候，然后询问他们是否有需要帮助的地方。考虑到用户可能没有明确的问题，保持友好和开放的态度很重要。接下来，我应该用简洁的语言表达欢迎，并邀请他们提出问题，这样既符合用户需求，又不会显得冗长。不需要调用任何工具，因为用户的问题还没被处理。所以直接回复即可。\\n\\u003c/think\\u003e\\n\\n你好！有什么可以帮助你的吗？\",\"type\":\"AI\"}', '2025-08-10 15:39:53');
INSERT INTO `chat_session` VALUES (1954447760845967362, 2, '277e176c-fb89-4038-91f2-e5f1a017bc4f', 104, '{\"contents\":[{\"text\":\"天空是蓝色的吗\",\"type\":\"TEXT\"}],\"type\":\"USER\"}', '2025-08-10 15:40:33');
INSERT INTO `chat_session` VALUES (1954447760845967363, 2, '277e176c-fb89-4038-91f2-e5f1a017bc4f', 105, '{\"text\":\"\\u003cthink\\u003e\\n好的，用户问“天空是蓝色的吗”。首先，我需要确认用户的问题是否需要调用工具来回答。但根据提供的工具，sum、queryCourse和squareRoot都是数学或课程查询相关的函数，但用户的问题并没有涉及这些内容。sum是用来加法的，queryCourse是查询课程，squareRoot是求平方根，而用户的问题是关于天空的颜色，这与这些工具无关。所以，正确的做法是直接回答用户的问题，说明天空的颜色是蓝色的，但不需要使用任何工具。需要确保回答简洁明了，避免不必要的技术术语。\\n\\u003c/think\\u003e\\n\\n天空通常是蓝色的，但有时可能会有云层遮挡阳光导致天空看起来不同，比如出现红色或紫色。不过根据标准观测，天空通常呈现蓝色。\",\"type\":\"AI\"}', '2025-08-10 15:40:33');
INSERT INTO `chat_session` VALUES (1954457952987910145, 2, '277e176c-fb89-4038-91f2-e5f1a017bc4f', 106, '{\"contents\":[{\"text\":\"你好\",\"type\":\"TEXT\"}],\"type\":\"USER\"}', '2025-08-10 16:21:03');
INSERT INTO `chat_session` VALUES (1954457952987910146, 2, '277e176c-fb89-4038-91f2-e5f1a017bc4f', 107, '{\"text\":\"\\u003cthink\\u003e\\n好的，用户发来的是“你好”。首先，我需要确认用户的需求是什么。看起来用户只是打招呼，没有具体的请求。可能需要礼貌地回应，并询问是否有其他问题或需要帮助的地方。根据提供的工具，用户可能需要使用sum、queryCourse或squareRoot这些功能，但当前的信息不足以触发这些工具。因此，保持友好，提供进一步帮助的意愿是合适的。\\n\\u003c/think\\u003e\\n\\n您好！有什么可以帮助您的吗？需要帮忙做点什么吗？\",\"type\":\"AI\"}', '2025-08-10 16:21:03');
INSERT INTO `chat_session` VALUES (1954457995354574850, 2, '277e176c-fb89-4038-91f2-e5f1a017bc4f', 108, '{\"text\":\"\\u003cthink\\u003e\\n好的，用户问感冒能吃水果吗。首先，我需要考虑用户的健康状况和水果的安全性。感冒期间，身体处于恢复期，所以饮食应该保持清淡，避免油腻或刺激性食物。水果是好的选择，比如苹果、梨、香蕉这些富含水分和维生素的水果，有助于缓解感冒带来的不适。\\n\\n不过，用户可能没有意识到感冒期间需要注意哪些事项，或者他们可能有过类似问题。我需要确认用户是否有特定的水果偏好，或者是否需要建议具体的水果种类。此外，用户可能希望得到一些具体的建议，比如何时吃水果，或者注意事项。因此，在回答时应提供全面的信息，帮助用户做出明智的选择。\\n\\n同时，要确保回答友好，并鼓励用户根据自身情况调整饮食，同时关注身体的反应。这样用户不仅能得到信息，还能保持积极的态度，促进更好的恢复。\\n\\u003c/think\\u003e\\n\\n感冒期间可以适量食用水果，但要注意适量和温和。水果有助于缓解感冒症状，如润喉、止咳等。建议选择轻盈、易消化的水果，如苹果、梨、香蕉等，避免过量或刺激性水果。您可以根据自身情况选择合适的食物，同时关注身体的反应，保持自然饮食。需要我帮您具体推荐哪种水果吗？\",\"type\":\"AI\"}', '2025-08-10 16:21:13');

-- ----------------------------
-- Table structure for user_markdown_docs
-- ----------------------------
DROP TABLE IF EXISTS `user_markdown_docs`;
CREATE TABLE `user_markdown_docs`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
  `user_id` bigint NOT NULL COMMENT '上传用户ID',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '上传时的原始文件名',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '整个 Markdown 文本内容',
  `create_time` datetime NOT NULL COMMENT '上传时间',
  `update_time` datetime NOT NULL COMMENT '最近更新时间',
  `level` int NOT NULL COMMENT '文档切割等级',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE COMMENT '用户ID索引'
) ENGINE = InnoDB AUTO_INCREMENT = 1935242243233931266 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户上传的 Markdown 文档表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user_markdown_docs
-- ----------------------------
INSERT INTO `user_markdown_docs` VALUES (1935147484112863234, 2, '测试文本1.md', '# 1.如何提高学习效率\n\n提高学习效率可以从以下几个方面入手：\n\n1. **制定计划**：合理安排学习时间和任务，将学习内容分解成小目标，按计划逐步完成。\n\n1. **营造环境**：保持学习环境安静、整洁，减少干扰因素，有助于集中注意力。\n\n1. **多样化学习**：采用多种学习方式，如阅读、听讲、做笔记、实践操作等，加深对知识的理解和记忆。\n\n1. **定期复习**：根据艾宾浩斯遗忘曲线，及时复习所学内容，强化记忆，防止遗忘。\n\n1. **劳逸结合**：保证充足的睡眠和休息时间，适当进行运动和娱乐活动，避免过度疲劳，提高学习时的精神状态。\n\n# 2. 为什么天空是蓝色的？\n\n天空呈现蓝色是因为大气对太阳光的散射作用。太阳光由红、橙、黄、绿、蓝、靛、紫等多种颜色的光组成，其中蓝光的波长较短，更容易被大气中的气体分子和微小颗粒散射。当太阳光穿过大气层时，蓝光向四面八方散射，使得整个天空看起来呈现蓝色。而波长较长的红光等颜色的光，不容易被散射，能够直接穿过大气层，所以我们看到的太阳本身是偏红色或黄色的。\n\n# 3. 怎样挑选新鲜的水果？\n\n挑选新鲜水果可以参考以下方法：\n\n1. **观察外观**：新鲜水果表面色泽鲜艳、有光泽，无明显黑斑、腐烂或虫蛀痕迹。例如，苹果表皮光滑，没有凹陷或裂痕；香蕉表皮金黄，无大面积黑斑。\n\n1. **触摸感受**：用手轻轻按压水果，新鲜的水果通常软硬适中。比如，橙子有一定弹性，不会太软或太硬；西瓜表皮坚硬，拍打时声音清脆。\n\n1. **闻气味**：新鲜水果一般具有自然的清香气味。如果水果有刺鼻、腐臭等异味，很可能已经不新鲜了。\n\n1. **查看蒂部**：水果的蒂部是判断新鲜程度的重要部位。蒂部翠绿、湿润，说明水果比较新鲜；如果蒂部干枯、发黑，则表明水果存放时间较长。\n\n# 4. 什么是人工智能？\n\n人工智能（Artificial Intelligence，简称 AI）是研究、开发用于模拟、延伸和扩展人的智能的理论、方法、技术及应用系统的一门新的技术科学。它试图了解智能的实质，并生产出一种新的能以人类智能相似的方式做出反应的智能机器。人工智能的应用领域非常广泛，包括但不限于自然语言处理、图像识别、机器学习、机器人学、专家系统等。通过人工智能技术，机器可以实现诸如语音识别、智能推荐、自动驾驶、疾病诊断等功能，为人们的生活和工作带来极大的便利和创新。\n\n# 5. 如何预防感冒？\n\n预防感冒可以从以下几个方面做起：\n\n1. **增强免疫力**：保持均衡的饮食，摄入充足的蛋白质、维生素和矿物质；坚持适量的运动，如散步、跑步、瑜伽等；保证充足的睡眠，让身体得到充分的休息和恢复。\n\n1. **注意个人卫生**：勤洗手，使用肥皂或洗手液，按照正确的洗手方法，彻底清洁双手，避免经手传播病毒；保持居住环境的清洁卫生，定期开窗通风，保持空气流通。\n\n1. **避免接触传染源**：在感冒流行季节，尽量减少前往人员密集、空气不流通的场所；如果必须前往，建议佩戴口罩，降低感染风险；避免与感冒患者密切接触，防止被传染。\n\n1. **注意保暖**：根据天气变化及时增减衣物，避免着凉。尤其是在季节交替时，更要注意保暖，防止因受凉而引发感冒。\n', '2025-06-18 09:28:08', '2025-06-18 09:28:08', 2);
INSERT INTO `user_markdown_docs` VALUES (1935242243233931265, 2, '测试文本2.md', '# 互联网大公司盘点\n\n## 北京 \n\n微软、谷歌、亚马逊、Airbnb、Grab、Hulu、谷歌、印象笔记、FreeWheel、百度、网易、腾讯、阿里巴巴、京东、美团点评、华为、字节跳动、滴滴出行、小米、快手、京东数科、爱奇艺、\n\n去哪儿网、搜狗、知乎、新浪微博、新浪、携程、豆瓣、360、奇安信、好未来、高途、猿辅导、新东方、作业帮、VIPKID、粉笔、美术宝、编程猫、开课吧、逻辑思维、商汤科技、旷视科技、依图科技、第四范式、寒武纪、地平线机器人、贝壳找房、我爱我家、58同城、自如网、瓜子二手车、汽车之家、易车网、完美世界、金山软件、西山居、陌陌、英雄互娱、一点资讯、搜狐、用友、映客直播、美图、牛客网、多抓鱼、博思软件、天眼查、猎豹移动、凤凰网、同程艺龙、最右、乐元素、掌趣科技、祖龙娱乐、乐逍遥、当当网、雪球、唱吧、每日优鲜、值得买科技、央视网、搜房网、转转、美菜网、便利蜂、boss直聘、智联招聘、前程无忧、领英、拉勾、新氧、美丽说、猫眼娱乐、途家、Keep、闪送、途牛、微店、友盟、36氪、趣头条、阅文集团、高德地图、墨刀、豌豆荚、触宝、脉脉、极客公园、马蜂窝、果壳、竞技世界、掌趣科技、昆仑万维、像素科技、唱吧、优酷、暴风影音、Shopee、神测数据、乐信、Moka、顺丰科技、永辉科技、小鹏汽车、蔚来、三七互娱、货拉拉、广联达、联想、Oracle、饿了么、OPPO、瑞斯康达、荣耀、中国人寿、Lazada、哔哩哔哩、Bigo、水滴、keep、松果出行、网商银行、蚂蚁金服、掌阅、民生科技、平安科技、小红书、趣加、快看世界、爱回收、联合利华、北森云、咪咕、趋势科技、老虎集团、美柚、乐信、美丽说、创新工厂、墨迹天气、中文在线、科大讯飞\n\n## 上海 \n\n阿里巴巴、腾讯、百度、京东、网易、字节跳动、蚂蚁集团、美团点评、拼多多、滴滴出行、哔哩哔哩、携程、饿了么、阅文集团、快手、哈啰出行、趣头条、得物、唯品会、叮咚买菜、小红书、返利网、达达、微盟、宝尊电商、沪江英语、英语流利说、掌门1对1、满帮、声网、陆金所、中国平安、众安在线、360数科、东方财富、米哈游、莉莉丝、三七互娱、游族网络、巨人网络、盛趣游戏、心动网络、TapTap、小牛互娱、58同城、链家、途虎养车、收钱吧、前程无忧、七牛云、世纪佳缘、泛微网络、云从科技、依图、旷视、触宝、商汤科技、同程艺龙、途牛、驴妈妈、樊登读书、蜻蜓FM、喜马拉雅、即刻、爱奇艺、虎扑、蔚来、拍拍贷、1号店、WiFi万能钥匙、网鱼网咖、Soul、盒马、爱奇艺\n\n##  深圳 \n\n腾讯、百度、阿里巴巴、字节跳动、快手、京东、顺丰、华为、中兴、OPPO、vivo、大疆、一加\n\n、柔宇、深信服、有赞、迅雷、金蝶、Shopee、中国平安、招银网络、微众银行、富途证券、商汤科技、小鹅通、货拉拉、编程猫、知识星球、XMind、珍爱网、广发证券、魅族科技、随手记、本地宝、菜鸟网络、丰巢科技、乐信、土巴兔、互联在线、shein\n\n## 广州 \n\n阿里巴巴、字节跳动、腾讯、网易、唯品会、欢聚时代、荔枝FM、UC、爱范儿、4399、Bigo、Shein、三七互娱、太平洋、ZAKER、科大讯飞、小鹏汽车、多益网络、汇量科技、携程、中兴、映客直播、Fordeal、深海游戏、中国电信、酷狗、妈妈网、超级课程表、金山、神州数码、机智云、56、有米\n\n## 杭州 \n\n阿里巴巴、网易、腾讯、百度、京东、美团、字节跳动、华为、蚂蚁金服、滴滴、快手、菜鸟网络、花瓣网、中移杭研、蘑菇街、同花顺、51信用卡、有赞、Zoom、思科、海康威视、曹操出行、哈啰出行、浙江大华、vivo、OPPO、魅族、旷视科技、商汤科技、涂鸦智能、e签宝、婚礼纪、丁香园、大搜车、贝壳、口碑网、微店、挖财、酷家乐、昵图网、掌盟、微医、恒生电子、连连支付、微拍堂、新华三\n\n## 南京 \n\n华为、中兴、华为、深信服、小米、字节跳动、满帮、途牛、苏宁、阿里巴巴、腾讯、360、京东、OPPO、vivo、T3出行、Shein、趋势科技、滴滴、58同城、科大讯飞、网宿科技、孩子王、yoho有货、烽火科技、快手、育儿网、三星、东方财富、南京证券、南京银行、华泰证券、焦点科技、帆软、千米网、浩鲸科技、地平线、快钱、扇贝、西祠胡同、车300\n\n## 武汉 \n\n阿里巴巴、字节跳动、腾讯、小米、华为、金山、斗鱼、京东、小红书、西山居、奇安信、360、三七互娱、风行网、明源云、尚德、途虎养车、微派、旷视科技、初心科技、神测数据、丰巢、出门问问、木仓科技、ThoughtWorks、烽火科技、中兴、宝宝树、多益网络、盛天网络、绿盟科技、他趣、神州数码、高途、猿辅导、作业帮、好未来、华图教育、数字政通、银联商务、科大讯飞、微众银行、长江证券、人人网、中文在线、掌游科技、极验、绿盟\n\n## 成都 \n\n腾讯、阿里巴巴、蚂蚁金服、字节跳动、华为、百度、网易、快手、新浪、OPPO、京东、美团、滴滴、完美世界、陌陌、聚美优品、爱奇艺、三七互娱、百词斩、科大讯飞、商汤科技、携程、联想、极米科技、鲁大师、盛大、多益网络、搜狗\n\n## 厦门 \n\n美团、4399、美图、吉比特、美柚、网宿科技、稿定科技、趣店、飞鱼科技、极致游戏、高德地图、字节跳动、宝宝树、网龙、快快网络、神州优车、站长之家、雷霆游戏、咪咕\n\n## 重庆 \n\n阿里巴巴、腾讯、字节跳动、小米、百度、京东、美团、万年历、科大讯飞、vivo、中兴、联想、浪潮、海康威视、新华三、美的、猪八戒网、中移物联网、完美世界、蚂蚁金服、云从科技\n\n## 长沙 \n\n腾讯、芒果tv、腾讯云、深信服、中兴、华为、CSDN、浩鲸科技、映客直播、荔枝FM、天鹅到家、问卷星、爱奇艺、亚信、福米科技、御泥坊\n\n## 郑州 \n\n阿里巴巴、数字郑州、华为、字节跳动、浪潮科技、开源中国、猿辅导、浩鲸、新华三、中移在线、中原消费金融、猎豹移动、中原银行、UU跑腿、爱云校、海康威视、亚信科技、神州信息、APUS、猪八戒网、新开普\n\n## 西安 \n\n阿里巴巴、华为、京东、腾讯云、字节跳动、小米、广联达、浙江大华、海康威视、360、奇安信、科大讯飞、大疆、OPPO、绿盟、当当网、寒武纪、优信二手车、神测数据、葡萄城、中兴、移动、联通、荣耀、Thougtworks、SAP、三星、汇丰、爱立信、邮储银行\n\n## 苏州 \n\n微软、Zoom、华为、360、Momenta、企查查、收钱吧、科大讯飞、同程艺龙、京东、锐捷、建信金科、中银金科、极智嘉、中移苏州研究院、飞利浦、金蝶\n\n## 天津 \n\n云账户、紫光云、联想、捷信、神州优车、科大讯飞、浪潮云、麒麟软件、深信服、中兴、渤海新行、天弘基金、南大通用、中科曙光、渣打银行、国家超级计算天津中心\n\n## 青岛 \n\n什么值得买、IT之家、科大讯飞、易企天创、海尔\n\n## 东莞 \n\n华为，vivo，OPPO、步步高、115科技、健客网、唯一网络\n', '2025-06-18 15:44:41', '2025-06-18 15:44:41', 2);

-- ----------------------------
-- Table structure for user_session
-- ----------------------------
DROP TABLE IF EXISTS `user_session`;
CREATE TABLE `user_session`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `tag` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `session_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '会话ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE COMMENT '用户与会话关联表'
) ENGINE = InnoDB AUTO_INCREMENT = 1931702636819091458 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user_session
-- ----------------------------
INSERT INTO `user_session` VALUES (2, 129, '12345', NULL, '1', '2025-06-07 19:56:42');
INSERT INTO `user_session` VALUES (1931594147723476993, 2, '会话名称1234', '软件与通信', '277e176c-fb89-4038-91f2-e5f1a017bc4f', '2025-06-08 14:08:28');
INSERT INTO `user_session` VALUES (1931649327437549569, 2, '6666', '3', '238d00c8-7b0a-45cc-9721-88a370264da8', '2025-06-08 17:47:42');

SET FOREIGN_KEY_CHECKS = 1;
