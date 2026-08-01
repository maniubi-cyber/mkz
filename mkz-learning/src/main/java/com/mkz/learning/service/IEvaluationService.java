package com.mkz.learning.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mkz.api.dto.leanring.EvaluationScoreDTO;
import com.mkz.common.domain.dto.PageDTO;
import com.mkz.learning.domain.dto.EvaluationDTO;
import com.mkz.learning.domain.po.Evaluation;
import com.mkz.learning.domain.po.InteractionQuestion;
import com.mkz.learning.domain.query.EvaluationQuery;
import com.mkz.learning.domain.vo.EvaluationDetailVO;
import com.mkz.learning.domain.vo.EvaluationVO;

import java.util.List;

/**
 * @author fsq
 * @date 2025/5/22 11:39
 */
public interface IEvaluationService extends IService<Evaluation> {

    PageDTO<EvaluationVO> queryEvaluationPage(EvaluationQuery query);

    void saveEvaluation(EvaluationDTO dto);

    EvaluationDTO queryEvaluationById(Long id);

    Boolean updateEvaluation(EvaluationDTO dto);

    Boolean deleteEvaluation(Long id);

    Boolean isEvaluated(Long courseId);

    EvaluationDetailVO queryEvaluationDetailById(Long id);

    void  getAllCourseAvgScore();
}
