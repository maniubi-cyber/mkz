package com.tianji.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.chat.domain.po.ApprovalRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批记录Mapper
 */
@Mapper
public interface ApprovalRecordMapper extends BaseMapper<ApprovalRecord> {
}
