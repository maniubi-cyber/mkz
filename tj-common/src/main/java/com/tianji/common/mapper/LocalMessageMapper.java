package com.tianji.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.common.domain.po.LocalMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 本地消息表Mapper
 */
@Mapper
public interface LocalMessageMapper extends BaseMapper<LocalMessage> {

    /**
     * 查询待补偿的消息（发送失败或待发送，且到达重试时间）
     *
     * @param currentTime 当前时间
     * @param limit       限制数量
     * @return 消息列表
     */
    @Select("SELECT * FROM local_message " +
            "WHERE status IN (0, 3) " +
            "AND next_retry_time <= #{currentTime} " +
            "AND retry_count < max_retry_count " +
            "ORDER BY create_time ASC " +
            "LIMIT #{limit}")
    List<LocalMessage> selectPendingMessages(@Param("currentTime") LocalDateTime currentTime, 
                                              @Param("limit") int limit);

    /**
     * 更新消息状态为发送中
     *
     * @param id 消息ID
     * @return 影响行数
     */
    @Update("UPDATE local_message SET status = 1, retry_count = retry_count + 1, " +
            "next_retry_time = DATE_ADD(NOW(), INTERVAL 5 MINUTE), update_time = NOW() " +
            "WHERE id = #{id} AND status IN (0, 3)")
    int updateStatusToSending(@Param("id") Long id);

    /**
     * 更新消息状态为发送成功
     *
     * @param id 消息ID
     * @return 影响行数
     */
    @Update("UPDATE local_message SET status = 2, update_time = NOW() WHERE id = #{id}")
    int updateStatusToSuccess(@Param("id") Long id);

    /**
     * 更新消息状态为发送失败
     *
     * @param id       消息ID
     * @param errorMsg 错误信息
     * @return 影响行数
     */
    @Update("UPDATE local_message SET status = 3, error_msg = #{errorMsg}, update_time = NOW() WHERE id = #{id}")
    int updateStatusToFailed(@Param("id") Long id, @Param("errorMsg") String errorMsg);

    /**
     * 根据业务ID查询消息（幂等性校验）
     *
     * @param businessId 业务ID
     * @return 消息记录
     */
    @Select("SELECT * FROM local_message WHERE business_id = #{businessId} AND status = 2 LIMIT 1")
    LocalMessage selectByBusinessId(@Param("businessId") String businessId);

    /**
     * 删除过期已发送消息
     *
     * @param status     消息状态（已发送成功）
     * @param expireTime 过期时间
     * @return 删除条数
     */
    @Update("DELETE FROM local_message WHERE status = #{status} AND create_time < #{expireTime}")
    int deleteByStatusAndTime(@Param("status") int status, @Param("expireTime") LocalDateTime expireTime);
}
