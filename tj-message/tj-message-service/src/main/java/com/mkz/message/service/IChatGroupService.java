package com.mkz.message.service;

import com.mkz.common.domain.dto.PageDTO;
import com.mkz.message.domain.dto.ChatGroupDTO;
import com.mkz.message.domain.dto.ChatMessageDTO;
import com.mkz.message.domain.dto.GroupMemberDTO;
import com.mkz.message.domain.po.ChatGroup;
import com.mkz.message.domain.po.ChatMessage;
import com.mkz.message.domain.query.ChatGroupQuery;
import com.mkz.message.domain.vo.ChatGroupVO;

import java.util.List;

public interface IChatGroupService {
    Long createGroup(ChatGroupDTO groupDTO);
    List<ChatGroupVO> getUserGroups(ChatGroupQuery query,Long userId);
    void addGroupMember(Long groupId, GroupMemberDTO memberDTO);
    void removeGroupMember(Long groupId, Long userId);
    PageDTO<ChatGroupVO> getAllGroups(ChatGroupQuery query);

    void addToGroup(Long groupId, Long userId);
}