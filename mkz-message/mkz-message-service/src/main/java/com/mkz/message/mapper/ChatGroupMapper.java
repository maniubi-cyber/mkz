package com.mkz.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mkz.message.domain.po.ChatGroup;
import com.mkz.message.domain.po.SmsThirdPlatform;
import com.mkz.message.domain.po.UserConversation;
import com.mkz.message.domain.query.ChatGroupQuery;
import com.mkz.message.domain.query.UserConversationQuery;
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
public interface ChatGroupMapper extends BaseMapper<ChatGroup> {
    IPage<ChatGroup> selectUserGroups(
            @Param("page") Page<?> page,
            @Param("userId") Long userId,
            @Param("query") ChatGroupQuery query
    );
}
