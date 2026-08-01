package com.mkz.live.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 直播开始消息（RocketMQ 消息体）
 */
@Data
@ApiModel(description = "直播开始消息")
public class LiveStartMsgDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty("直播间id")
    private Long liveId;

    @ApiModelProperty("直播标题")
    private String title;

    @ApiModelProperty("讲师id")
    private Long teacherId;

    @ApiModelProperty("实际开始时间")
    private LocalDateTime startTime;

    @ApiModelProperty("已报名用户id列表")
    private List<Long> userIds;
}
