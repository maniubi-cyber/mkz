package com.mkz.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mkz.chat.domain.dto.UserSessionDTO;
import com.mkz.chat.domain.po.ChatSession;
import com.mkz.chat.domain.po.UserSession;
import com.mkz.common.domain.query.PageQuery;

import java.util.List;

/**
 * <p>
 * 聊天对话的每个片段记录（分片存储） 服务类
 * </p>
 *
 * @author lusy
 * @since 2025-05-06
 */
public interface IUserSessionService extends IService<UserSession> {


    UserSession createUserSession(UserSessionDTO dto);

    void deleteUserSession(Long id);

    List<UserSession> getUserSessionList();

    void updateUserSession( Long id,UserSessionDTO dto);
}
