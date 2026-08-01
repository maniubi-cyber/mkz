package com.mkz.api.dto.live;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 直播结束消息（RocketMQ 跨服务消息体：mkz-live 发布，供消息中心等订阅）
 */
@Data
@ApiModel(description = "直播结束消息")
public class LiveStopMsgDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty("直播间id")
    private Long liveId;

    @ApiModelProperty("直播标题")
    private String title;

    @ApiModelProperty("实际结束时间")
    private LocalDateTime endTime;

    @ApiModelProperty("回放地址（可为空）")
    private String playbackUrl;

    @ApiModelProperty("已报名用户id列表")
    private List<Long> userIds;
}
