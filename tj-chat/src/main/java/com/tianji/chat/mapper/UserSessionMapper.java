package com.mkz.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkz.chat.domain.po.ChatSession;
import com.mkz.chat.domain.po.UserSession;

/**
 * <p>
 * 聊天对话的每个片段记录（分片存储） Mapper 接口
 * </p>
 *
 * @author lusy
 * @since 2025-05-06
 */
public interface UserSessionMapper extends BaseMapper<UserSession> {

}
