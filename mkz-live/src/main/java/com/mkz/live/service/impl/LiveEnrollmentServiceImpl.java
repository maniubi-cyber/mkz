package com.mkz.live.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mkz.common.domain.dto.PageDTO;
import com.mkz.common.exceptions.BadRequestException;
import com.mkz.common.utils.BeanUtils;
import com.mkz.common.utils.CollUtils;
import com.mkz.common.utils.UserContext;
import com.mkz.live.domain.po.LiveEnrollment;
import com.mkz.live.domain.po.LiveRoom;
import com.mkz.live.domain.query.LiveRoomQuery;
import com.mkz.live.domain.vo.LiveRoomVO;
import com.mkz.live.mapper.LiveEnrollmentMapper;
import com.mkz.live.service.ILiveEnrollmentService;
import com.mkz.live.service.ILiveRoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 直播报名服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LiveEnrollmentServiceImpl extends ServiceImpl<LiveEnrollmentMapper, LiveEnrollment>
        implements ILiveEnrollmentService {

    private final ILiveRoomService liveRoomService;

    @Override
    public void enroll(Long liveId) {
        Long userId = requireUserId();
        LiveRoom room = liveRoomService.getById(liveId);
        if (room == null) {
            throw new BadRequestException("直播房间不存在");
        }
        if (room.getStatus() != null && room.getStatus() == 2) {
            throw new BadRequestException("直播已结束，无法报名");
        }
        boolean exists = this.lambdaQuery()
                .eq(LiveEnrollment::getLiveId, liveId)
                .eq(LiveEnrollment::getUserId, userId)
                .count() > 0;
        if (exists) {
            // 幂等：已报名直接返回
            return;
        }
        LiveEnrollment enrollment = new LiveEnrollment()
                .setLiveId(liveId)
                .setUserId(userId);
        this.save(enrollment);
    }

    @Override
    public void cancel(Long liveId) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            return;
        }
        this.lambdaUpdate()
                .eq(LiveEnrollment::getLiveId, liveId)
                .eq(LiveEnrollment::getUserId, userId)
                .remove();
    }

    @Override
    public Boolean isEnrolled(Long liveId) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            return false;
        }
        return this.lambdaQuery()
                .eq(LiveEnrollment::getLiveId, liveId)
                .eq(LiveEnrollment::getUserId, userId)
                .count() > 0;
    }

    @Override
    public PageDTO<LiveRoomVO> queryMyEnrollments(LiveRoomQuery query) {
        Long userId = requireUserId();
        Page<LiveEnrollment> page = this.page(query.toMpPageDefaultSortByCreateTimeDesc(),
                new LambdaQueryWrapper<LiveEnrollment>()
                        .eq(LiveEnrollment::getUserId, userId));
        if (CollUtils.isEmpty(page.getRecords())) {
            return PageDTO.empty(page);
        }
        List<Long> liveIds = page.getRecords().stream()
                .map(LiveEnrollment::getLiveId)
                .collect(Collectors.toList());
        Map<Long, LiveRoom> roomMap = liveRoomService.listByIds(liveIds).stream()
                .collect(Collectors.toMap(LiveRoom::getId, r -> r));
        List<LiveRoomVO> vos = page.getRecords().stream()
                .map(e -> BeanUtils.copyBean(roomMap.get(e.getLiveId()), LiveRoomVO.class))
                .collect(Collectors.toList());
        return PageDTO.of(page, vos);
    }

    private Long requireUserId() {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new BadRequestException("用户未登录");
        }
        return userId;
    }
}
