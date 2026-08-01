package com.mkz.message.service;

import com.mkz.message.domain.dto.NoticeTemplateDTO;
import com.mkz.message.domain.dto.NoticeTemplateDetailDTO;
import com.mkz.message.domain.dto.NoticeTemplateFormDTO;
import com.mkz.message.domain.query.NoticeTemplatePageQuery;
import com.mkz.common.domain.dto.PageDTO;
import com.mkz.message.domain.po.NoticeTemplate;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 通知模板 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-08-19
 */
public interface INoticeTemplateService extends IService<NoticeTemplate> {

    Long saveNoticeTemplate(NoticeTemplateFormDTO noticeTemplateFormDTO);

    void updateNoticeTemplate(NoticeTemplateFormDTO noticeTemplateFormDTO);

    PageDTO<NoticeTemplateDTO> queryNoticeTemplates(NoticeTemplatePageQuery pageQuery);

    NoticeTemplateDetailDTO queryNoticeTemplate(Long id);

    NoticeTemplate queryByCode(String code);
}
