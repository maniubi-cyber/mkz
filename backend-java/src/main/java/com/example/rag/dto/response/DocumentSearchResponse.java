package com.example.rag.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档搜索结果响应 DTO
 *
 * <p>包含搜索高亮信息，用于前端展示匹配的关键词高亮。</p>
 *
 * @author knowledge-rag-team
 */
@Data
@Builder
@Schema(description = "文档搜索结果")
public class DocumentSearchResponse {

    @Schema(description = "文档ID")
    private Long id;

    @Schema(description = "文档标题（含高亮标签）")
    private String title;

    @Schema(description = "文档标题（原始文本）")
    private String titlePlain;

    @Schema(description = "文档内容摘要（含高亮标签）")
    private String contentSnippet;

    @Schema(description = "高亮片段列表")
    private List<String> highlightFragments;

    @Schema(description = "知识库ID")
    private Long kbId;

    @Schema(description = "知识库名称")
    private String kbName;

    @Schema(description = "作者ID")
    private Long ownerId;

    @Schema(description = "作者名")
    private String ownerName;

    @Schema(description = "文件类型")
    private String fileType;

    @Schema(description = "相关度评分")
    private Float score;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
