package com.mkz.live.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 直播房间
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("live_room")
public class LiveRoom implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 关联课程id */
    private Long courseId;

    /** 讲师id */
    private Long teacherId;

    /** 直播标题 */
    private String title;

    /** 封面图 */
    private String coverUrl;

    /** 直播简介 */
    private String introduce;

    /** 状态：0-未开始，1-直播中，2-已结束 */
    private Integer status;

    /** 计划开始时间 */
    private LocalDateTime startTime;

    /** 计划结束时间 */
    private LocalDateTime endTime;

    /** 实际开始时间 */
    private LocalDateTime actualStartTime;

    /** 实际结束时间 */
    private LocalDateTime actualEndTime;

    /** 推流地址(rtmp) */
    private String pushUrl;

    /** 直播播放地址(flv/hls) */
    private String playUrl;

    /** 回放地址 */
    private String playbackUrl;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
