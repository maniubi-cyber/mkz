package com.mkz.api.dto.msg;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder//加入buider注解可以不用手动去new
@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")//staticName:快速生成有参构造 of函数
public class LikedTimesDTO {
    /**
     * 点赞的业务id
     */
    private Long bizId;
    /**
     * 总的点赞次数
     */
    private Integer likedTimes;
    /**
     * 点赞业务类型（QA/NOTE/COMMENT），消费端据此路由更新对应业务表
     */
    private String bizType;
}
