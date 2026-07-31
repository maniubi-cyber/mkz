package com.example.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.rag.entity.DocumentVersionHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档版本历史 Mapper
 *
 * @author knowledge-rag-team
 */
@Mapper
public interface DocumentVersionHistoryMapper extends BaseMapper<DocumentVersionHistory> {
}
