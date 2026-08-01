package com.mkz.learning.service;

import com.mkz.common.domain.dto.PageDTO;
import com.mkz.common.domain.query.PageQuery;
import com.mkz.learning.domain.dto.LearningPlanDTO;
import com.mkz.learning.domain.po.LearningLesson;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mkz.learning.domain.vo.LearningLessonVO;
import com.mkz.learning.domain.vo.LearningPlanPageVO;

import java.util.List;

/**
 * <p>
 * 学生课程表 服务类
 * </p>
 *
 * @author fsq
 * @since 2023-10-22
 */
public interface ILearningLessonService extends IService<LearningLesson> {

    void addUserLesson(Long userId, List<Long> courseIds);

    PageDTO<LearningLessonVO> queryMyLessons(PageQuery query);

    LearningLessonVO queryMyCurrentLesson();

    LearningLessonVO queryLessonByCourseId(Long courseId);

    Long isLessonValid(Long courseId);

    Integer countLearningLessonByCourse(Long courseId);

    void createLearningPlan(LearningPlanDTO dto);

    LearningPlanPageVO queryMyPlans(PageQuery query);

    void deleteMyLessons(Long id);

    /**
     * 按用户 + 课程删除课表记录（退款场景使用）
     *
     * @param userId   用户ID（来自退款消息，不依赖线程级 UserContext）
     * @param courseId 课程ID
     */
    void deleteUserLesson(Long userId, Long courseId);
}
