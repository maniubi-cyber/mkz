package com.mkz.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mkz.message.domain.po.SmsThirdPlatform;
import com.mkz.message.domain.po.UserConversation;
import com.mkz.message.domain.po.UserPrivateMessage;
import com.mkz.message.domain.query.UserConversationQuery;
import com.mkz.message.domain.query.UserPrivateMessageQuery;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 第三方云通讯平台 Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2022-08-20
 */
public interface UserPrivateMessageMapper extends BaseMapper<UserPrivateMessage> {

    IPage<UserPrivateMessage> getMessageHistory(
            @Param("page") Page<?> page,
            @Param("query") UserPrivateMessageQuery query,
            @Param("userId") Long userId
    );
}
