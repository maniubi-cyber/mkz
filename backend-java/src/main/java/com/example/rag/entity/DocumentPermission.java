package com.example.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档权限实体 - 细粒度权限控制
 *
 * <p>支持对单个文档设置用户级权限（READ/WRITE/ADMIN），
 * 实现灵活的文档协作权限管理。</p>
 *
 * @author knowledge-rag-team
 */
@Data
@TableName("document_permission")
public class DocumentPermission {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 文档 ID */
    private Long documentId;

    /** 用户ID */
    private Long userId;

    /** 组织ID */
    private Long orgId;

    /** 权限类型：READ / WRITE / ADMIN */
    private String permission;

    /** 授权人ID */
    private Long grantBy;

    /** 创建时间 */
    private LocalDateTime createTime;
}
