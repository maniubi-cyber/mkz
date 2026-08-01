package com.mkz.live.domain.query;

import com.mkz.common.domain.query.PageQuery;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 直播房间分页查询条件
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel(description = "直播房间分页查询条件")
public class LiveRoomQuery extends PageQuery {

    @ApiModelProperty("状态：0-未开始，1-直播中，2-已结束")
    private Integer status;

    @ApiModelProperty("关联课程id")
    private Long courseId;

    @ApiModelProperty("讲师id")
    private Long teacherId;

    @ApiModelProperty("计划开始时间（起始）")
    private LocalDateTime startTime;
}
