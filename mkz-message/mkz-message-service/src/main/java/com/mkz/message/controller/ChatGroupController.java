package com.mkz.message.controller;

import com.mkz.common.domain.dto.PageDTO;
import com.mkz.common.utils.UserContext;
import com.mkz.message.domain.dto.ChatGroupDTO;
import com.mkz.message.domain.dto.ChatMessageDTO;
import com.mkz.message.domain.dto.GroupMemberDTO;
import com.mkz.message.domain.po.ChatGroup;
import com.mkz.message.domain.po.ChatMessage;
import com.mkz.message.domain.query.ChatGroupQuery;
import com.mkz.message.domain.query.ChatHistoryQuery;
import com.mkz.message.domain.vo.ChatGroupVO;
import com.mkz.message.domain.vo.ChatMessageVO;
import com.mkz.message.service.IChatGroupService;
import com.mkz.message.service.IChatMessageService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "聊天功能控制器")
@RestController
@RequestMapping("chat")
@RequiredArgsConstructor
public class ChatGroupController {

    private final IChatGroupService groupService;
    private final IChatMessageService messageService;

    @ApiOperation("创建群组")
    @PostMapping("groups")
    public Long createGroup(@RequestBody ChatGroupDTO groupDTO) {
        return groupService.createGroup(groupDTO);
    }

    @ApiOperation("查询全部群组")
    @GetMapping("groups/all")
    public PageDTO<ChatGroupVO>  getAllGroups(ChatGroupQuery query) {
        return groupService.getAllGroups(query);
    }

    @ApiOperation("获取用户群组列表")
    @GetMapping("groups")
    public List<ChatGroupVO> getUserGroups(ChatGroupQuery query) {
        Long userId = UserContext.getUser();
        return groupService.getUserGroups(query,userId);
    }

    @ApiOperation("用户加群")
    @PostMapping("groups/{groupId}")
    public void addGroupMember(@PathVariable Long groupId) {
        Long userId = UserContext.getUser();
        groupService.addToGroup(groupId, userId);
    }


    @ApiOperation("添加群成员")
    @PostMapping("groups/{groupId}/members")
    public void addGroupMember(
            @PathVariable Long groupId,
            @RequestBody GroupMemberDTO memberDTO) {
        groupService.addGroupMember(groupId, memberDTO);
    }

    @ApiOperation("移除群成员")
    @DeleteMapping("groups/{groupId}/members/{userId}")
    public void removeGroupMember(
            @PathVariable Long groupId,
            @PathVariable Long userId) {
        groupService.removeGroupMember(groupId, userId);
    }

    @ApiOperation("发送消息")
    @PostMapping("messages")
    public Long sendMessage(@RequestBody ChatMessageDTO messageDTO) {
        return messageService.sendMessage(messageDTO);
    }

    @ApiOperation("获取历史消息")
    @GetMapping("messages")
    public PageDTO<ChatMessageVO> getHistoryMessages(ChatHistoryQuery query) {
        return messageService.getHistoryMessages(query);
    }

//    @ApiOperation("标记消息已读")
//    @PostMapping("messages/read")
//    public void markMessagesAsRead(@RequestBody List<Long> messageIds) {
//        messageService.markMessagesAsRead(messageIds);
//    }
}