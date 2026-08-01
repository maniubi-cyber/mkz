package com.mkz.live.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 直播房间视图
 */
@Data
@ApiModel(description = "直播房间视图")
public class LiveRoomVO {

    @ApiModelProperty("直播间id")
    private Long id;

    @ApiModelProperty("关联课程id")
    private Long courseId;

    @ApiModelProperty("讲师id")
    private Long teacherId;

    @ApiModelProperty("直播标题")
    private String title;

    @ApiModelProperty("封面图")
    private String coverUrl;

    @ApiModelProperty("直播简介")
    private String introduce;

    @ApiModelProperty("状态：0-未开始，1-直播中，2-已结束")
    private Integer status;

    @ApiModelProperty("计划开始时间")
    private LocalDateTime startTime;

    @ApiModelProperty("计划结束时间")
    private LocalDateTime endTime;

    @ApiModelProperty("实际开始时间")
    private LocalDateTime actualStartTime;

    @ApiModelProperty("实际结束时间")
    private LocalDateTime actualEndTime;

    @ApiModelProperty("推流地址")
    private String pushUrl;

    @ApiModelProperty("播放地址")
    private String playUrl;

    @ApiModelProperty("回放地址")
    private String playbackUrl;

    @ApiModelProperty("当前用户是否已报名")
    private Boolean enrolled;

    @ApiModelProperty("创建时间")
    private LocalDateTime createTime;
}
