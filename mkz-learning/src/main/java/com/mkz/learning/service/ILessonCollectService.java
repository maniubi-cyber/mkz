package com.mkz.learning.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mkz.common.domain.dto.PageDTO;
import com.mkz.learning.domain.dto.CollectFormDTO;
import com.mkz.learning.domain.po.LessonCollect;
import com.mkz.learning.domain.query.LessonCollectQuery;
import com.mkz.learning.domain.vo.LessonCollectVO;

/**
 * @author fsq
 * @date 2025/5/22 9:04
 */
public interface ILessonCollectService extends IService<LessonCollect> {

    PageDTO<LessonCollectVO> queryMyCollects(LessonCollectQuery query);

    void addCollect(CollectFormDTO dto);

    Boolean isCollected(Long lessonId);
}
