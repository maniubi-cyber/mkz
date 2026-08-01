package com.mkz.learning.service;

import com.mkz.common.domain.dto.PageDTO;
import com.mkz.learning.domain.dto.ReplyDTO;
import com.mkz.learning.domain.po.InteractionReply;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mkz.learning.domain.query.ReplyPageQuery;
import com.mkz.learning.domain.vo.QuestionVO;
import com.mkz.learning.domain.vo.ReplyVO;

/**
 * <p>
 * 互动问题的回答或评论 服务类
 * </p>
 *
 * @author fsq
 * @since 2023-10-23
 */
public interface IInteractionReplyService extends IService<InteractionReply> {

    void saveReply(ReplyDTO dto);

    void hiddenReply(Long id, Boolean hidden);

    PageDTO<ReplyVO> queryReplyPage(ReplyPageQuery pageQuery, boolean forAdmin);

    ReplyVO queryReplyById(Long id);
}
