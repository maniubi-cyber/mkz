package com.mkz.live.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mkz.api.dto.live.LiveStartMsgDTO;
import com.mkz.api.dto.live.LiveStopMsgDTO;
import com.mkz.common.autoconfigure.mq.RocketMqHelper;
import com.mkz.common.constants.MqConstants;
import com.mkz.common.domain.dto.PageDTO;
import com.mkz.common.exceptions.BadRequestException;
import com.mkz.common.utils.BeanUtils;
import com.mkz.common.utils.CollUtils;
import com.mkz.common.utils.StringUtils;
import com.mkz.common.utils.UserContext;
import com.mkz.live.domain.dto.LiveRoomFormDTO;
import com.mkz.live.domain.po.LiveEnrollment;
import com.mkz.live.domain.po.LiveRoom;
import com.mkz.live.domain.query.LiveRoomQuery;
import com.mkz.live.domain.vo.LiveRoomVO;
import com.mkz.live.mapper.LiveEnrollmentMapper;
import com.mkz.live.mapper.LiveRoomMapper;
import com.mkz.live.service.ILiveRoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 直播房间服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LiveRoomServiceImpl extends ServiceImpl<LiveRoomMapper, LiveRoom> implements ILiveRoomService {

    private final LiveEnrollmentMapper enrollmentMapper;
    private final RocketMqHelper rocketMqHelper;

    @Override
    public Long createRoom(LiveRoomFormDTO dto) {
        LiveRoom room = BeanUtils.copyBean(dto, LiveRoom.class);
        if (room == null) {
            room = new LiveRoom();
        }
        room.setId(null);
        room.setStatus(0);
        this.save(room);
        return room.getId();
    }

    @Override
    public void updateRoom(LiveRoomFormDTO dto) {
        if (dto.getId() == null) {
            throw new BadRequestException("直播间id不能为空");
        }
        if (this.getById(dto.getId()) == null) {
            throw new BadRequestException("直播房间不存在");
        }
        LiveRoom update = BeanUtils.copyBean(dto, LiveRoom.class);
        update.setId(dto.getId());
        this.updateById(update);
    }

    @Override
    public LiveRoomVO queryRoomDetail(Long id) {
        LiveRoom room = this.getById(id);
        if (room == null) {
            throw new BadRequestException("直播房间不存在");
        }
        LiveRoomVO vo = BeanUtils.copyBean(room, LiveRoomVO.class);
        Long userId = UserContext.getUser();
        if (userId != null) {
            vo.setEnrolled(queryEnrolledLiveIds(userId, Collections.singletonList(id)).contains(id));
        }
        return vo;
    }

    @Override
    public PageDTO<LiveRoomVO> queryRoomPage(LiveRoomQuery query) {
        LambdaQueryWrapper<LiveRoom> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getCourseId() != null, LiveRoom::getCourseId, query.getCourseId())
                .eq(query.getTeacherId() != null, LiveRoom::getTeacherId, query.getTeacherId())
                .eq(query.getStatus() != null, LiveRoom::getStatus, query.getStatus())
                .ge(query.getStartTime() != null, LiveRoom::getStartTime, query.getStartTime());
        Page<LiveRoom> page = this.page(query.toMpPageDefaultSortByCreateTimeDesc(), wrapper);
        if (CollUtils.isEmpty(page.getRecords())) {
            return PageDTO.empty(page);
        }
        List<Long> liveIds = page.getRecords().stream().map(LiveRoom::getId).collect(Collectors.toList());
        Set<Long> enrolledIds = queryEnrolledLiveIds(UserContext.getUser(), liveIds);
        List<LiveRoomVO> vos = page.getRecords().stream().map(room -> {
            LiveRoomVO vo = BeanUtils.copyBean(room, LiveRoomVO.class);
            vo.setEnrolled(enrolledIds.contains(room.getId()));
            return vo;
        }).collect(Collectors.toList());
        return PageDTO.of(page, vos);
    }

    @Override
    public void startLive(Long id) {
        LiveRoom room = this.getById(id);
        if (room == null) {
            throw new BadRequestException("直播房间不存在");
        }
        if (room.getStatus() != null && room.getStatus() == 2) {
            throw new BadRequestException("直播已结束，无法再次开始");
        }
        LiveRoom update = new LiveRoom();
        update.setId(id);
        update.setStatus(1);
        update.setActualStartTime(LocalDateTime.now());
        this.updateById(update);

        // 发布直播开始消息，异步通知已报名用户（RocketMQ 解耦）
        LiveStartMsgDTO msg = new LiveStartMsgDTO();
        msg.setLiveId(id);
        msg.setTitle(room.getTitle());
        msg.setTeacherId(room.getTeacherId());
        msg.setStartTime(LocalDateTime.now());
        msg.setUserIds(queryEnrolledUserIds(id));
        boolean sent = rocketMqHelper.sendSync(MqConstants.Topic.LIVE_TOPIC,
                MqConstants.Tag.LIVE_START_TAG, "live-start-" + id, msg);
        if (!sent) {
            // 消息发送失败：当前骨架仅记录日志，后续可接入本地消息表 + XXL-Job 补偿
            log.error("直播开始消息发送失败，liveId={}", id);
        }
    }

    @Override
    public void stopLive(Long id, String playbackUrl) {
        LiveRoom room = this.getById(id);
        if (room == null) {
            throw new BadRequestException("直播房间不存在");
        }
        LiveRoom update = new LiveRoom();
        update.setId(id);
        update.setStatus(2);
        update.setActualEndTime(LocalDateTime.now());
        if (StringUtils.isNotBlank(playbackUrl)) {
            update.setPlaybackUrl(playbackUrl);
        }
        this.updateById(update);
        // 发布直播结束消息（回放生成、停播提醒等由订阅方处理）
        publishStopEvent(room);
    }

    @Override
    public int closeExpiredRooms() {
        LocalDateTime now = LocalDateTime.now();
        List<LiveRoom> rooms = this.lambdaQuery()
                .eq(LiveRoom::getStatus, 1)
                .isNotNull(LiveRoom::getEndTime)
                .lt(LiveRoom::getEndTime, now)
                .list();
        if (CollUtils.isEmpty(rooms)) {
            return 0;
        }
        int closed = 0;
        for (LiveRoom room : rooms) {
            LiveRoom update = new LiveRoom();
            update.setId(room.getId());
            update.setStatus(2);
            if (room.getActualEndTime() == null) {
                update.setActualEndTime(now);
            }
            this.updateById(update);
            publishStopEvent(room);
            closed++;
        }
        return closed;
    }

    /**
     * 发布直播结束消息
     */
    private void publishStopEvent(LiveRoom room) {
        LiveStopMsgDTO msg = new LiveStopMsgDTO();
        msg.setLiveId(room.getId());
        msg.setTitle(room.getTitle());
        msg.setEndTime(LocalDateTime.now());
        boolean sent = rocketMqHelper.sendSync(MqConstants.Topic.LIVE_TOPIC,
                MqConstants.Tag.LIVE_STOP_TAG, "live-stop-" + room.getId(), msg);
        if (!sent) {
            // 消息发送失败：当前仅记录日志，后续可接入本地消息表 + XXL-Job 补偿
            log.error("直播结束消息发送失败，liveId={}", room.getId());
        }
    }

    /**
     * 查询用户在指定直播间集合中的报名情况
     */
    private Set<Long> queryEnrolledLiveIds(Long userId, Collection<Long> liveIds) {
        if (userId == null || CollUtils.isEmpty(liveIds)) {
            return Collections.emptySet();
        }
        List<LiveEnrollment> enrollments = enrollmentMapper.selectList(
                new LambdaQueryWrapper<LiveEnrollment>()
                        .eq(LiveEnrollment::getUserId, userId)
                        .in(LiveEnrollment::getLiveId, liveIds));
        return enrollments.stream().map(LiveEnrollment::getLiveId).collect(Collectors.toSet());
    }

    /**
     * 查询某直播间的全部报名用户id
     */
    private List<Long> queryEnrolledUserIds(Long liveId) {
        List<LiveEnrollment> enrollments = enrollmentMapper.selectList(
                new LambdaQueryWrapper<LiveEnrollment>()
                        .eq(LiveEnrollment::getLiveId, liveId));
        return enrollments.stream().map(LiveEnrollment::getUserId).collect(Collectors.toList());
    }
}
