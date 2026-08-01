package com.mkz.chat.config;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 对话摘要压缩器
 * 
 * 针对多轮对话中上下文窗口溢出导致早期信息丢失的问题，设计滑动窗口 + 摘要压缩策略：
 * - 最近N轮对话原文保留
 * - 超出部分由LLM动态压缩为语义摘要存入Redis
 * - 并checkpoint持久化
 * - 在防止上下文撑爆的前提下，既节省Token消耗又保证长对话的连贯性
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SummaryCompressor {

    private final StringRedisTemplate redisTemplate;

    /**
     * 保留的最近N轮对话原文
     */
    @Value("${chat.memory.window-size:10}")
    private int windowSize;

    /**
     * 摘要压缩Redis Key前缀
     */
    private static final String SUMMARY_KEY_PREFIX = "chat:summary:";

    /**
     * 摘要过期时间（7天）
     */
    private static final long SUMMARY_EXPIRE_DAYS = 7L;

    /**
     * 消息内容提取正则
     */
    private static final Pattern TEXT_PATTERN = Pattern.compile("\"text\":\"([^\"]+)\"");

    /**
     * 压缩对话历史为摘要
     *
     * @param sessionId 会话ID
     * @param messages  完整对话历史
     * @return 压缩后的对话（最近N轮原文 + 摘要）
     */
    public List<ChatMessage> compressIfNeeded(String sessionId, List<ChatMessage> messages) {
        if (messages == null || messages.size() <= windowSize * 2) {
            // 对话轮数不超过窗口大小，无需压缩
            return messages;
        }

        log.info("对话历史超过窗口大小，开始压缩，sessionId: {}, 消息数: {}", sessionId, messages.size());

        // 1. 分离最近N轮对话和超出部分
        int splitIndex = messages.size() - windowSize * 2;
        List<ChatMessage> oldMessages = messages.subList(0, splitIndex);
        List<ChatMessage> recentMessages = messages.subList(splitIndex, messages.size());

        // 2. 将旧消息压缩为摘要
        String summary = compressMessagesToSummary(oldMessages);

        // 3. 将摘要存入Redis
        String summaryKey = SUMMARY_KEY_PREFIX + sessionId;
        redisTemplate.opsForValue().set(summaryKey, summary, SUMMARY_EXPIRE_DAYS, TimeUnit.DAYS);

        log.info("对话摘要压缩完成，sessionId: {}, 摘要长度: {}", sessionId, summary.length());

        // 4. 构建压缩后的消息列表：摘要作为系统消息 + 最近N轮原文
        List<ChatMessage> compressedMessages = new java.util.ArrayList<>();
        compressedMessages.add(dev.langchain4j.data.message.SystemMessage.from(
                "以下是之前对话的摘要：\n" + summary + "\n\n请基于以上上下文继续回答用户问题。"
        ));
        compressedMessages.addAll(recentMessages);

        return compressedMessages;
    }

    /**
     * 获取会话摘要
     *
     * @param sessionId 会话ID
     * @return 摘要内容
     */
    public String getSummary(String sessionId) {
        String summaryKey = SUMMARY_KEY_PREFIX + sessionId;
        return redisTemplate.opsForValue().get(summaryKey);
    }

    /**
     * 将消息列表压缩为摘要文本
     * 实际项目中可调用LLM进行语义压缩，这里提供基础实现
     *
     * @param messages 消息列表
     * @return 摘要文本
     */
    private String compressMessagesToSummary(List<ChatMessage> messages) {
        StringBuilder summary = new StringBuilder();
        summary.append("【对话历史摘要】\n");

        // 提取关键信息
        String lastUserQuestion = "";
        String lastAiAnswer = "";

        for (ChatMessage message : messages) {
            if (message instanceof UserMessage) {
                lastUserQuestion = extractTextFromMessage(message.toString());
            } else if (message instanceof AiMessage) {
                lastAiAnswer = extractTextFromMessage(message.toString());
            }
        }

        if (!lastUserQuestion.isEmpty()) {
            summary.append("用户最近关注：").append(truncateText(lastUserQuestion, 200)).append("\n");
        }
        if (!lastAiAnswer.isEmpty()) {
            summary.append("助手主要回复：").append(truncateText(lastAiAnswer, 300)).append("\n");
        }

        summary.append("共 ").append(messages.size() / 2).append(" 轮对话");

        return summary.toString();
    }

    /**
     * 从消息JSON中提取文本内容
     */
    private String extractTextFromMessage(String messageJson) {
        Matcher matcher = TEXT_PATTERN.matcher(messageJson);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    /**
     * 截断文本
     */
    private String truncateText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    /**
     * 清除会话摘要
     *
     * @param sessionId 会话ID
     */
    public void clearSummary(String sessionId) {
        String summaryKey = SUMMARY_KEY_PREFIX + sessionId;
        redisTemplate.delete(summaryKey);
    }
}
