package com.mkz.live.domain.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 直播房间表单
 */
@Data
@ApiModel(description = "直播房间表单")
public class LiveRoomFormDTO {

    @ApiModelProperty("直播间id（编辑时必传）")
    private Long id;

    @ApiModelProperty("关联课程id")
    private Long courseId;

    @ApiModelProperty("讲师id")
    private Long teacherId;

    @ApiModelProperty("直播标题")
    @NotBlank(message = "直播标题不能为空")
    private String title;

    @ApiModelProperty("封面图")
    private String coverUrl;

    @ApiModelProperty("直播简介")
    private String introduce;

    @ApiModelProperty("计划开始时间")
    @NotNull(message = "计划开始时间不能为空")
    private LocalDateTime startTime;

    @ApiModelProperty("计划结束时间")
    private LocalDateTime endTime;

    @ApiModelProperty("推流地址")
    private String pushUrl;

    @ApiModelProperty("播放地址")
    private String playUrl;
}
