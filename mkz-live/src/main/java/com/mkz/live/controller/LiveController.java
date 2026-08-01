package com.mkz.live.controller;

import com.mkz.common.domain.dto.PageDTO;
import com.mkz.live.domain.dto.LiveRoomFormDTO;
import com.mkz.live.domain.query.LiveRoomQuery;
import com.mkz.live.domain.vo.LiveRoomVO;
import com.mkz.live.service.ILiveEnrollmentService;
import com.mkz.live.service.ILiveRoomService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 直播管理接口
 */
@RestController
@RequiredArgsConstructor
@Api(tags = "直播管理")
@RequestMapping("/live")
public class LiveController {

    private final ILiveRoomService liveRoomService;
    private final ILiveEnrollmentService liveEnrollmentService;

    @ApiOperation("创建直播房间")
    @PostMapping("/room")
    public Long createRoom(@Validated @RequestBody LiveRoomFormDTO dto) {
        return liveRoomService.createRoom(dto);
    }

    @ApiOperation("修改直播房间")
    @PutMapping("/room")
    public void updateRoom(@Validated @RequestBody LiveRoomFormDTO dto) {
        liveRoomService.updateRoom(dto);
    }

    @ApiOperation("分页查询直播房间")
    @GetMapping("/room/page")
    public PageDTO<LiveRoomVO> queryRoomPage(LiveRoomQuery query) {
        return liveRoomService.queryRoomPage(query);
    }

    @ApiOperation("查询直播房间详情")
    @GetMapping("/room/{id}")
    public LiveRoomVO queryRoomDetail(@PathVariable Long id) {
        return liveRoomService.queryRoomDetail(id);
    }

    @ApiOperation("开始直播")
    @PostMapping("/room/{id}/start")
    public void startLive(@PathVariable Long id) {
        liveRoomService.startLive(id);
    }

    @ApiOperation("结束直播")
    @PostMapping("/room/{id}/stop")
    public void stopLive(@PathVariable Long id,
                         @RequestParam(value = "playbackUrl", required = false) String playbackUrl) {
        liveRoomService.stopLive(id, playbackUrl);
    }

    @ApiOperation("报名直播")
    @PostMapping("/room/{id}/enroll")
    public void enroll(@PathVariable Long id) {
        liveEnrollmentService.enroll(id);
    }

    @ApiOperation("取消报名直播")
    @DeleteMapping("/room/{id}/enroll")
    public void cancelEnroll(@PathVariable Long id) {
        liveEnrollmentService.cancel(id);
    }

    @ApiOperation("当前用户是否已报名该直播")
    @GetMapping("/room/{id}/enrolled")
    public Boolean isEnrolled(@PathVariable Long id) {
        return liveEnrollmentService.isEnrolled(id);
    }

    @ApiOperation("分页查询我报名的直播")
    @GetMapping("/my/enrollments")
    public PageDTO<LiveRoomVO> queryMyEnrollments(LiveRoomQuery query) {
        return liveEnrollmentService.queryMyEnrollments(query);
    }
}
