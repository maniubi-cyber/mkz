package com.mkz.media.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkz.media.domain.po.FileChunkUpload;
import org.apache.ibatis.annotations.Mapper;

/**
 * FileChunkUpload 实体对应的 Mapper 接口
 */
@Mapper
public interface FileChunkUploadMapper extends BaseMapper<FileChunkUpload> {
}