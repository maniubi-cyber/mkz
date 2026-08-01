package com.mkz.live.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mkz.common.domain.dto.PageDTO;
import com.mkz.live.domain.dto.LiveRoomFormDTO;
import com.mkz.live.domain.po.LiveRoom;
import com.mkz.live.domain.query.LiveRoomQuery;
import com.mkz.live.domain.vo.LiveRoomVO;

/**
 * 直播房间服务
 */
public interface ILiveRoomService extends IService<LiveRoom> {

    /**
     * 创建直播房间
     */
    Long createRoom(LiveRoomFormDTO dto);

    /**
     * 修改直播房间
     */
    void updateRoom(LiveRoomFormDTO dto);

    /**
     * 查询直播房间详情
     */
    LiveRoomVO queryRoomDetail(Long id);

    /**
     * 分页查询直播房间
     */
    PageDTO<LiveRoomVO> queryRoomPage(LiveRoomQuery query);

    /**
     * 开始直播（发布直播开始消息）
     */
    void startLive(Long id);

    /**
     * 结束直播（记录回放地址，发布直播结束消息）
     */
    void stopLive(Long id, String playbackUrl);

    /**
     * 定时关播：关闭已过计划结束时间仍处于直播中的房间，返回关闭数量
     */
    int closeExpiredRooms();
}
