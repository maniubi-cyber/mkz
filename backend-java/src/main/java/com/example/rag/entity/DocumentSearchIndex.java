package com.example.rag.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.LocalDateTime;

/**
 * Elasticsearch 文档搜索索引实体
 *
 * <p>用于全文检索的 ES 索引结构，配合 IK 中文分词器实现高效搜索。</p>
 *
 * <h3>性能对比：</h3>
 * <ul>
 *   <li>MySQL LIKE '%keyword%': > 1500ms（10万+数据量）</li>
 *   <li>Elasticsearch + IK: ~60ms</li>
 * </ul>
 *
 * @author knowledge-rag-team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "document_search")
@Setting(settingPath = "/elasticsearch/document_search_settings.json")
public class DocumentSearchIndex {

    @Id
    @Field(type = FieldType.Long)
    private Long id;

    /** 文档标题 - IK分词 */
    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart"),
            otherFields = {
                    @InnerField(suffix = "keyword", type = FieldType.Keyword)
            }
    )
    private String title;

    /** 文档内容 - IK分词 */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String content;

    /** 知识库ID */
    @Field(type = FieldType.Long)
    private Long kbId;

    /** 作者ID */
    @Field(type = FieldType.Long)
    private Long ownerId;

    /** 作者名 */
    @Field(type = FieldType.Keyword)
    private String ownerName;

    /** 可见范围 */
    @Field(type = FieldType.Keyword)
    private String visibility;

    /** 组织ID */
    @Field(type = FieldType.Long)
    private Long orgId;

    /** 文件类型 */
    @Field(type = FieldType.Keyword)
    private String fileType;

    /** 创建时间 */
    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis)
    private LocalDateTime createTime;

    /** 更新时间 */
    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis)
    private LocalDateTime updateTime;

    /** 切片数量 */
    @Field(type = FieldType.Integer)
    private Integer chunkCount;
}
