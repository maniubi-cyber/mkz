package com.mkz.live.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mkz.common.domain.dto.PageDTO;
import com.mkz.live.domain.po.LiveEnrollment;
import com.mkz.live.domain.query.LiveRoomQuery;
import com.mkz.live.domain.vo.LiveRoomVO;

/**
 * 直播报名服务
 */
public interface ILiveEnrollmentService extends IService<LiveEnrollment> {

    /**
     * 报名直播
     */
    void enroll(Long liveId);

    /**
     * 取消报名直播
     */
    void cancel(Long liveId);

    /**
     * 当前用户是否已报名该直播
     */
    Boolean isEnrolled(Long liveId);

    /**
     * 分页查询我报名的直播
     */
    PageDTO<LiveRoomVO> queryMyEnrollments(LiveRoomQuery query);
}
