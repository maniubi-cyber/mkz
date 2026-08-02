package com.mkz.learning.mq;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mkz.api.dto.msg.LikedTimesDTO;
import com.mkz.common.constants.MqConstants;
import com.mkz.common.mq.AbstractIdempotentListener;
import com.mkz.common.utils.CollUtils;
import com.mkz.common.utils.MessageIdempotentUtil;
import com.mkz.learning.domain.po.Evaluation;
import com.mkz.learning.domain.po.InteractionReply;
import com.mkz.learning.domain.po.Note;
import com.mkz.learning.service.IEvaluationService;
import com.mkz.learning.service.IInteractionReplyService;
import com.mkz.learning.service.INoteService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 点赞数变更监听器
 * <p>
 * 消费 mkz-remark 发布的点赞数消息（topic=like_record），按消息体 bizType 路由：
 * QA → interaction_reply、NOTE → note、COMMENT → evaluation，批量更新对应业务表的点赞数。
 * 与生产端统一走 RocketMQ + 幂等消费（keys 作幂等键），替代原 RabbitMQ 链路。
 */
@Component
@Slf4j
@RocketMQMessageListener(
        topic = MqConstants.Topic.LIKE_RECORD_TOPIC,
        consumerGroup = MqConstants.ConsumerGroup.LIKED_RECORD_GROUP,
        selectorExpression = "*",
        messageModel = MessageModel.CLUSTERING
)
public class LikedRecordListener extends AbstractIdempotentListener<List<LikedTimesDTO>>
        implements RocketMQListener<MessageExt> {

    private static final String BIZ_TYPE_QA = "QA";
    private static final String BIZ_TYPE_NOTE = "NOTE";
    private static final String BIZ_TYPE_COMMENT = "COMMENT";

    private final IInteractionReplyService replyService;
    private final INoteService noteService;
    private final IEvaluationService evaluationService;
    private final ObjectMapper objectMapper;

    public LikedRecordListener(MessageIdempotentUtil idempotentUtil,
                               IInteractionReplyService replyService,
                               INoteService noteService,
                               IEvaluationService evaluationService,
                               ObjectMapper objectMapper) {
        super(idempotentUtil);
        this.replyService = replyService;
        this.noteService = noteService;
        this.evaluationService = evaluationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(MessageExt message) {
        String businessId = message.getKeys();
        List<LikedTimesDTO> list;
        try {
            list = objectMapper.readValue(message.getBody(), new TypeReference<List<LikedTimesDTO>>() {});
        } catch (IOException e) {
            log.error("解析点赞数消息失败，keys={}, tags={}", businessId, message.getTags(), e);
            return;
        }
        if (CollUtils.isEmpty(list)) {
            return;
        }
        // keys 为空时用 tag+首个bizId 兜底，保证幂等键稳定
        if (businessId == null) {
            businessId = message.getTags() + "-" + list.get(0).getBizId();
        }
        consume(businessId, list);
    }

    @Override
    protected void doConsume(List<LikedTimesDTO> list) {
        List<InteractionReply> replies = new ArrayList<>();
        List<Note> notes = new ArrayList<>();
        List<Evaluation> evaluations = new ArrayList<>();
        for (LikedTimesDTO dto : list) {
            if (dto.getBizType() == null || dto.getBizId() == null || dto.getLikedTimes() == null) {
                log.warn("点赞数消息字段缺失，跳过: {}", dto);
                continue;
            }
            switch (dto.getBizType()) {
                case BIZ_TYPE_QA: {
                    InteractionReply reply = new InteractionReply();
                    reply.setLikedTimes(dto.getLikedTimes());
                    reply.setId(dto.getBizId());
                    replies.add(reply);
                    break;
                }
                case BIZ_TYPE_NOTE: {
                    Note note = new Note();
                    note.setLikedTimes(dto.getLikedTimes());
                    note.setId(dto.getBizId());
                    notes.add(note);
                    break;
                }
                case BIZ_TYPE_COMMENT: {
                    Evaluation evaluation = new Evaluation();
                    evaluation.setHelpCount(dto.getLikedTimes());
                    evaluation.setId(dto.getBizId());
                    evaluations.add(evaluation);
                    break;
                }
                default:
                    log.warn("未知点赞业务类型，跳过: {}", dto.getBizType());
            }
        }
        if (CollUtils.isNotEmpty(replies)) {
            replyService.updateBatchById(replies);
        }
        if (CollUtils.isNotEmpty(notes)) {
            noteService.updateBatchById(notes);
        }
        if (CollUtils.isNotEmpty(evaluations)) {
            evaluationService.updateBatchById(evaluations);
        }
        log.info("点赞数批量更新完成，QA={}条，NOTE={}条，COMMENT={}条", replies.size(), notes.size(), evaluations.size());
    }
}
