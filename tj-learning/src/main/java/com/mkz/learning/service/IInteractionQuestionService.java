package com.mkz.learning.service;

import com.mkz.common.domain.dto.PageDTO;
import com.mkz.learning.domain.dto.QuestionFormDTO;
import com.mkz.learning.domain.po.InteractionQuestion;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mkz.learning.domain.query.QuestionAdminPageQuery;
import com.mkz.learning.domain.query.QuestionPageQuery;
import com.mkz.learning.domain.vo.QuestionAdminVO;
import com.mkz.learning.domain.vo.QuestionVO;

/**
 * <p>
 * 互动提问的问题表 服务类
 * </p>
 *
 * @author fsq
 * @since 2023-10-23
 */
public interface IInteractionQuestionService extends IService<InteractionQuestion> {

    void saveQuestion(QuestionFormDTO dto);

    void updateQuestion(Long id, QuestionFormDTO dto);

    PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query);

    QuestionVO queryQuestionById(Long id);

    PageDTO<QuestionAdminVO> queryQuestionAdminVOPage(QuestionAdminPageQuery query);

    void deleteQuestion(Long id);

    void hiddenQuestion(Long id, boolean hidden);

    QuestionAdminVO queryQuestionAdminById(Long id);
}
