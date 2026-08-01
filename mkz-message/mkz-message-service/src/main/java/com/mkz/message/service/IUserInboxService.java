package com.mkz.message.service;

import com.mkz.message.domain.dto.UserInboxDTO;
import com.mkz.api.dto.user.UserDTO;
import com.mkz.message.domain.query.UserInboxQuery;
import com.mkz.common.domain.dto.PageDTO;
import com.mkz.message.domain.po.NoticeTemplate;
import com.mkz.message.domain.po.UserInbox;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 * 用户通知记录 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-08-19
 */
public interface IUserInboxService extends IService<UserInbox> {

    void saveNoticeToInbox(NoticeTemplate noticeTemplate, List<UserDTO> users);

    PageDTO<UserInboxDTO> queryUserInBoxesPage(UserInboxQuery query);

    Integer getUnReadCountByType(Integer type);

    Integer getUnReadCount();

    Boolean markMessageAsRead(Long id);

    Boolean markAllMessagesAsRead();
}
