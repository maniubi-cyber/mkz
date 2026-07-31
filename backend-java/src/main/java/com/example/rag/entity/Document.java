package com.example.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档表实体 - 支持乐观锁并发编辑
 *
 * <p>使用 MyBatis-Plus @Version 注解实现无侵入式乐观锁控制。
 * 多人同时编辑同一文档时，后提交者会因 version 不匹配而更新失败，
 * 从而避免覆盖先提交者的内容。</p>
 *
 * <h3>乐观锁工作原理：</h3>
 * <pre>
 *   UPDATE document SET ..., version = version + 1
 *    WHERE id = ? AND version = ?
 *    -- 如果受影响行数为0，说明版本号已变化，抛出 OptimisticLockerException
 * </pre>
 *
 * @author knowledge-rag-team
 */
@Data
@TableName("document")
public class Document {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属知识库 ID */
    private Long kbId;

    /** 文档标题 */
    private String title;

    /** 文档内容（Markdown格式） */
    private String content;

    /** 文档HTML渲染内容 */
    private String contentHtml;

    /** 文件 MD5（用于去重，上传文档时） */
    private String fileMd5;

    /** 原始文件名 */
    private String fileName;

    /** 文件类型：pdf / docx / xlsx / md / txt */
    private String fileType;

    /** 文件大小（字节） */
    private Long fileSize;

    /** MinIO 存储路径 */
    private String minioPath;

    /** 解析状态：PENDING / PARSING / SUCCESS / FAILED */
    private String parseStatus;

    /** 解析失败原因 */
    private String parseFailMsg;

    /** 上传用户 ID */
    private Long ownerId;

    /** 权限范围：PRIVATE / PUBLIC / ORG */
    private String visibility;

    /** 组织 ID（ORG 可见时必填） */
    private Long orgId;

    /** 浏览次数 */
    private Integer viewCount;

    /** 软删除：0正常 1已删除 */
    @TableLogic
    private Integer isDeleted;

    /** 切片数量 */
    private Integer chunkCount;

    /** 乐观锁版本号（用于并发编辑冲突检测） */
    @Version
    private Integer version;

    /** 最后编辑者ID */
    private Long lastEditorId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
