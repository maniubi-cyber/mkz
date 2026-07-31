package com.tianji.chat.config;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.tianji.chat.constants.RedisConstants;
import com.tianji.chat.domain.dto.PromptBuilder;
import com.tianji.chat.domain.po.ChatSession;
import com.tianji.chat.domain.po.UserSession;
import com.tianji.chat.service.IChatSessionService;
import com.tianji.chat.service.IUserSessionService;
import com.tianji.chat.utils.DataDelayTaskHandler;
import com.tianji.common.utils.UserContext;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messageToJson;

/**
 * 增强版对话记忆存储
 * 
 * 实现滑动窗口 + 摘要压缩策略：
 * 1. 最近N轮对话原文保留
 * 2. 超出部分由LLM动态压缩为语义摘要存入Redis
 * 3. 会话状态通过Redis checkpoint持久化
 * 4. 服务重启后对话从断点恢复，无需用户重述上下文
 * 
 * 全链路特性：
 * - 防止上下文撑爆
 * - 节省Token消耗
 * - 保证长对话的连贯性
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "chat.memory.enhanced", havingValue = "true", matchIfMissing = false)
public class EnhancedChatMemoryStore implements ChatMemoryStore {

    private final StringRedisTemplate redisTemplate;
    private final IChatSessionService chatSessionService;
    private final DataDelayTaskHandler dataDelayTaskHandler;
    private final IUserSessionService userSessionService;
    private final SummaryCompressor summaryCompressor;

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    /**
     * 保留的最近N轮对话原文
     */
    private static final int WINDOW_SIZE = 10;

    /**
     * Checkpoint Key前缀
     */
    private static final String CHECKPOINT_KEY_PREFIX = "chat:checkpoint:";

    /**
     * Checkpoint过期时间（30天）
     */
    private static final long CHECKPOINT_EXPIRE_DAYS = 30L;

    private String getKey(Object sessionId) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            UserSession one = userSessionService.lambdaQuery().eq(UserSession::getSessionId, sessionId).one();
            userId = one.getUserId();
        }
        return RedisConstants.CHAT_MEMORY_KEY_PREFIX + userId + ":" + sessionId;
    }

    /**
     * 获取checkpoint key
     */
    private String getCheckpointKey(Object sessionId) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            UserSession one = userSessionService.lambdaQuery().eq(UserSession::getSessionId, sessionId).one();
            userId = one.getUserId();
        }
        return CHECKPOINT_KEY_PREFIX + userId + ":" + sessionId;
    }

    @Override
    public List<ChatMessage> getMessages(Object sessionId) {
        try {
            // 1. 尝试从Redis获取完整对话历史
            List<String> messageList = redisTemplate.opsForList().range(getKey(sessionId), 0, -1);
            
            if (CollUtil.isNotEmpty(messageList)) {
                // 获取会话摘要
                String summary = summaryCompressor.getSummary(String.valueOf(sessionId));
                
                List<ChatMessage> messages = messagesFromJson(messageList.toString());
                
                // 如果有摘要，添加到消息开头
                if (summary != null && !summary.isEmpty()) {
                    List<ChatMessage> messagesWithSummary = new ArrayList<>();
                    messagesWithSummary.add(dev.langchain4j.data.message.SystemMessage.from(
                            "以下是之前对话的摘要：\n" + summary + "\n\n请基于以上上下文继续回答用户问题。"
                    ));
                    messagesWithSummary.addAll(messages);
                    return messagesWithSummary;
                }
                
                // 检查是否需要压缩
                if (messages.size() > WINDOW_SIZE * 2) {
                    return summaryCompressor.compressIfNeeded(String.valueOf(sessionId), messages);
                }
                
                return messages;
            }

            // 2. 从checkpoint恢复
            List<ChatMessage> checkpointMessages = restoreFromCheckpoint(sessionId);
            if (checkpointMessages != null && !checkpointMessages.isEmpty()) {
                log.info("从checkpoint恢复对话历史，sessionId: {}", sessionId);
                return checkpointMessages;
            }

            // 3. 从数据库获取
            List<ChatSession> chatSessionList = chatSessionService.lambdaQuery()
                    .eq(ChatSession::getUserId, UserContext.getUser())
                    .eq(ChatSession::getSessionId, sessionId)
                    .orderByAsc(ChatSession::getSegmentIndex)
                    .list();

            if (CollUtil.isNotEmpty(chatSessionList)) {
                messageList = chatSessionList.stream()
                        .map(ChatSession::getContent)
                        .collect(Collectors.toList());
                return messagesFromJson(messageList.toString());
            }

            return Collections.emptyList();
        } catch (Exception e) {
            log.error("读取对话历史失败，sessionId: {}", sessionId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public void updateMessages(Object sessionId, List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        try {
            // 过滤只保留用户消息和AI回复
            for (ChatMessage message : messages) {
                if (!(message instanceof UserMessage || message instanceof AiMessage)) {
                    return;
                }
            }

            ChatMessage chatMessage = messages.get(messages.size() - 1);
            String json = messageToJson(chatMessage);

            // 处理用户消息文本
            if (chatMessage instanceof UserMessage) {
                JSONObject root = JSON.parseObject(json);
                JSONArray contents = root.getJSONArray("contents");
                if (contents != null && !contents.isEmpty()) {
                    JSONObject firstContent = contents.getJSONObject(0);
                    String originalText = firstContent.getString("text");
                    String processedText = PromptBuilder.extractOriginalMessage(originalText);
                    firstContent.put("text", processedText);
                    json = root.toJSONString();
                }
            }

            // 存入Redis
            redisTemplate.opsForList().rightPush(getKey(sessionId), json);
            log.info("更新对话历史到Redis，sessionId: {}, message: {}", sessionId, json);

            // 检查是否需要压缩
            Long messageCount = redisTemplate.opsForList().size(getKey(sessionId));
            if (messageCount != null && messageCount > WINDOW_SIZE * 2) {
                triggerCompression(sessionId, messages);
            }

            // 定期保存checkpoint
            if (messageCount != null && messageCount % 5 == 0) {
                saveCheckpoint(sessionId, messages);
            }

            // 开启延时任务持久化到数据库
            Map<String, Object> map = new HashMap<>();
            map.put("key", getKey(sessionId));
            map.put("num", messages.size());
            String jsonStr = JSONUtil.toJsonStr(map);
            dataDelayTaskHandler.addDelayedTask(jsonStr, RedisConstants.DELAY_TASK_EXECUTE_TIME, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.error("更新对话历史失败，sessionId: {}", sessionId, e);
        }
    }

    @Override
    public void deleteMessages(Object sessionId) {
        try {
            redisTemplate.delete(getKey(sessionId));
            redisTemplate.delete(getCheckpointKey(String.valueOf(sessionId)));
            summaryCompressor.clearSummary(String.valueOf(sessionId));
            log.info("删除对话历史，sessionId: {}", sessionId);
        } catch (Exception e) {
            log.error("删除对话历史失败，sessionId: {}", sessionId, e);
        }
    }

    /**
     * 保存checkpoint
     */
    private void saveCheckpoint(Object sessionId, List<ChatMessage> messages) {
        try {
            String checkpointKey = getCheckpointKey(sessionId);
            List<String> jsonMessages = messages.stream()
                    .map(ChatMessageSerializer::messageToJson)
                    .collect(Collectors.toList());
            String checkpointData = JSON.toJSONString(jsonMessages);
            redisTemplate.opsForValue().set(checkpointKey, checkpointData, CHECKPOINT_EXPIRE_DAYS, TimeUnit.DAYS);
            log.debug("保存checkpoint成功，sessionId: {}", sessionId);
        } catch (Exception e) {
            log.error("保存checkpoint失败，sessionId: {}", sessionId, e);
        }
    }

    /**
     * 从checkpoint恢复
     */
    private List<ChatMessage> restoreFromCheckpoint(Object sessionId) {
        try {
            String checkpointKey = getCheckpointKey(sessionId);
            String checkpointData = redisTemplate.opsForValue().get(checkpointKey);
            if (checkpointData != null && !checkpointData.isEmpty()) {
                List<String> jsonMessages = JSON.parseArray(checkpointData, String.class);
                return messagesFromJson(jsonMessages.toString());
            }
        } catch (Exception e) {
            log.error("从checkpoint恢复失败，sessionId: {}", sessionId, e);
        }
        return null;
    }

    /**
     * 触发压缩
     */
    private void compressionIfNeeded(Object sessionId) {
        try {
            List<String> messageList = redisTemplate.opsForList().range(getKey(sessionId), 0, -1);
            if (CollUtil.isNotEmpty(messageList)) {
                List<ChatMessage> messages = messagesFromJson(messageList.toString());
                if (messages.size() > WINDOW_SIZE * 2) {
                    triggerCompression(sessionId, messages);
                }
            }
        } catch (Exception e) {
            log.error("压缩检查失败，sessionId: {}", sessionId, e);
        }
    }

    /**
     * 触发压缩
     */
    private void triggerCompression(Object sessionId, List<ChatMessage> messages) {
        executorService.submit(() -> {
            try {
                log.info("开始压缩对话历史，sessionId: {}, 消息数: {}", sessionId, messages.size());
                List<ChatMessage> compressed = summaryCompressor.compressIfNeeded(
                        String.valueOf(sessionId), messages);
                log.info("对话历史压缩完成，sessionId: {}, 压缩后消息数: {}", sessionId, compressed.size());
            } catch (Exception e) {
                log.error("对话历史压缩失败，sessionId: {}", sessionId, e);
            }
        });
    }
}
