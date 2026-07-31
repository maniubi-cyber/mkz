package com.example.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档版本历史实体 - 记录文档编辑历史
 *
 * <p>每次文档更新时，自动保存当前版本到历史表，
 * 支持版本回溯和编辑历史查看。</p>
 *
 * @author knowledge-rag-team
 */
@Data
@TableName("document_version_history")
public class DocumentVersionHistory {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 文档 ID */
    private Long documentId;

    /** 版本号 */
    private Integer version;

    /** 版本标题 */
    private String title;

    /** 版本内容 */
    private String content;

    /** 编辑者ID */
    private Long editorId;

    /** 编辑摘要 */
    private String editSummary;

    /** 创建时间 */
    private LocalDateTime createTime;
}
