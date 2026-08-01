package com.mkz.learning.service;

import com.mkz.api.dto.leanring.LearningLessonDTO;
import com.mkz.learning.domain.dto.LearningRecordFormDTO;
import com.mkz.learning.domain.po.LearningRecord;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 学习记录表 服务类
 * </p>
 *
 * @author fsq
 * @since 2023-10-22
 */
public interface ILearningRecordService extends IService<LearningRecord> {

    LearningLessonDTO queryLearningRecordByCourse(Long courseId);

    void addLearningRecord(LearningRecordFormDTO dto);

}
