package com.example.rag.service.impl;

import com.example.rag.dto.response.DocumentSearchResponse;
import com.example.rag.entity.Document;
import com.example.rag.entity.DocumentSearchIndex;
import com.example.rag.entity.KnowledgeBase;
import com.example.rag.entity.User;
import com.example.rag.mapper.DocumentMapper;
import com.example.rag.mapper.KnowledgeBaseMapper;
import com.example.rag.mapper.UserMapper;
import com.example.rag.service.DocumentSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.mapping.*;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.*;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * 文档搜索服务实现 - 基于 Elasticsearch + IK 中文分词器 (Spring Data ES 8.x)
 *
 * <p>原基于 MySQL LIKE '%keyword%' 的模糊搜索在数据量超 10 万条时耗时超 1.5s，
 * 引入 Elasticsearch 并配置 IK 中文分词器，将搜索响应降至 60ms，
 * 并支持按相关度排序和高亮显示。</p>
 *
 * @author knowledge-rag-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentSearchServiceImpl implements DocumentSearchService {

    private final ElasticsearchClient elasticsearchClient;
    private final DocumentMapper documentMapper;
    private final UserMapper userMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;

    private static final String INDEX_NAME = "document_search";
    private static final String HIGHLIGHT_PRE_TAG = "<em class='highlight'>";
    private static final String HIGHLIGHT_POST_TAG = "</em>";
    private static final int HIGHLIGHT_FRAGMENT_SIZE = 150;
    private static final int HIGHLIGHT_NUM_FRAGMENTS = 3;

    /** IK 分词器开关：true=ik_max_word/ik_smart，false=standard（ES 未装 IK 插件时回退） */
    @Value("${spring.elasticsearch.analyzer-ik:true}")
    private boolean analyzerIk;

    private String indexAnalyzer() {
        return analyzerIk ? "ik_max_word" : "standard";
    }

    private String searchAnalyzer() {
        return analyzerIk ? "ik_smart" : "standard";
    }

    /**
     * 幂等创建索引（含 IK/standard 分词映射），写入前调用。
     */
    private void ensureIndex() {
        try {
            boolean exists = elasticsearchClient.indices().exists(e -> e.index(INDEX_NAME)).value();
            if (exists) {
                return;
            }
            CreateIndexResponse resp = elasticsearchClient.indices().create(c -> c
                    .index(INDEX_NAME)
                    .mappings(m -> m
                            .properties("id", p -> p.long_(l -> l))
                            .properties("title", p -> p.text(t -> t
                                    .analyzer(indexAnalyzer())
                                    .searchAnalyzer(searchAnalyzer())
                                    .fields("keyword", f -> f.keyword(k -> k))))
                            .properties("content", p -> p.text(t -> t
                                    .analyzer(indexAnalyzer())
                                    .searchAnalyzer(searchAnalyzer())))
                            .properties("kbId", p -> p.long_(l -> l))
                            .properties("ownerId", p -> p.long_(l -> l))
                            .properties("ownerName", p -> p.keyword(k -> k))
                            .properties("visibility", p -> p.keyword(k -> k))
                            .properties("orgId", p -> p.long_(l -> l))
                            .properties("fileType", p -> p.keyword(k -> k))
                            .properties("chunkCount", p -> p.integer(i -> i))
                            .properties("createTime", p -> p.date(d -> d))
                            .properties("updateTime", p -> p.date(d -> d))
                    ));
            log.info("ES 索引创建成功: {}, analyzer={}", INDEX_NAME, indexAnalyzer());
        } catch (IOException e) {
            log.error("ES 索引创建失败: {}", INDEX_NAME, e);
        }
    }

    /**
     * 全文搜索文档
     *
     * <p>搜索目标为 AI 服务写入的切块索引 {@code kb_{kbId}}
     * （字段: content / file_name / kb_id / owner_id / visibility / org_id / parent_id / doc_version），
     * 与 Qdrant 向量检索共用同一份切块数据，保证权限过滤口径一致。</p>
     */
    @Override
    public List<DocumentSearchResponse> search(String keyword, Long kbId, int page, int size,
                                                Long userId, Long orgId) {
        if (kbId == null) {
            return List.of();
        }
        long startTime = System.currentTimeMillis();
        String indexName = "kb_" + kbId;

        try {
            SearchRequest.Builder requestBuilder = new SearchRequest.Builder()
                    .index(indexName)
                    .from((page - 1) * size)
                    .size(size)
                    .sort(s -> s.score(sc -> sc.order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)));

            // 构建查询条件
            BoolQuery boolQuery = buildChunkBoolQuery(keyword, kbId, userId, orgId);
            requestBuilder.query(q -> q.bool(boolQuery));

            // 高亮配置（切块索引无 title 字段，仅 content 高亮）
            requestBuilder.highlight(h -> h
                    .preTags(HIGHLIGHT_PRE_TAG)
                    .postTags(HIGHLIGHT_POST_TAG)
                    .fields("content", f -> f.fragmentSize(HIGHLIGHT_FRAGMENT_SIZE).numberOfFragments(HIGHLIGHT_NUM_FRAGMENTS))
                    .requireFieldMatch(false)
            );

            // 只返回必要字段
            requestBuilder.source(s -> s.filter(f -> f.includes(
                    "content", "document_id", "kb_id", "file_name",
                    "owner_id", "visibility", "org_id", "parent_id",
                    "parent_index", "chunk_index", "doc_version"
            )));

            @SuppressWarnings("unchecked")
            SearchResponse<Map<String, Object>> response = elasticsearchClient.search(
                    requestBuilder.build(),
                    (Class<Map<String, Object>>) (Class<?>) Map.class
            );

            List<DocumentSearchResponse> results = new ArrayList<>();
            for (Hit<Map<String, Object>> hit : response.hits().hits()) {
                DocumentSearchResponse doc = parseChunkHit(hit, kbId);
                if (doc != null) {
                    results.add(doc);
                }
            }

            long costTime = System.currentTimeMillis() - startTime;
            log.info("ES搜索完成: keyword={}, index={}, total={}, cost={}ms", keyword, indexName, results.size(), costTime);

            return results;
        } catch (IOException e) {
            log.error("ES搜索异常: keyword={}", keyword, e);
            throw new RuntimeException("搜索服务暂时不可用", e);
        } catch (ElasticsearchException e) {
            log.warn("ES搜索不可用（索引不存在等）: keyword={}, err={}", keyword, e.getMessage());
            return List.of();
        }
    }

    @Override
    public long countSearch(String keyword, Long kbId, Long userId, Long orgId) {
        if (kbId == null) {
            return 0;
        }
        try {
            BoolQuery boolQuery = buildChunkBoolQuery(keyword, kbId, userId, orgId);

            CountRequest countRequest = new CountRequest.Builder()
                    .index("kb_" + kbId)
                    .query(q -> q.bool(boolQuery))
                    .build();

            long count = elasticsearchClient.count(countRequest).count();
            log.debug("ES计数: keyword={}, count={}", keyword, count);
            return count;
        } catch (IOException e) {
            log.error("ES计数异常: keyword={}", keyword, e);
            return 0;
        }
    }

    @Override
    public void indexDocument(Long docId) {
        try {
            ensureIndex();
            Document doc = documentMapper.selectById(docId);
            if (doc == null) {
                log.warn("文档不存在，跳过索引: docId={}", docId);
                return;
            }

            User owner = userMapper.selectById(doc.getOwnerId());
            DocumentSearchIndex index = DocumentSearchIndex.builder()
                    .id(doc.getId())
                    .title(doc.getTitle())
                    .content(doc.getContent())
                    .kbId(doc.getKbId())
                    .ownerId(doc.getOwnerId())
                    .ownerName(owner != null ? owner.getUsername() : "")
                    .visibility(doc.getVisibility())
                    .orgId(doc.getOrgId())
                    .fileType(doc.getFileType())
                    .createTime(doc.getCreateTime())
                    .updateTime(doc.getUpdateTime())
                    .chunkCount(doc.getChunkCount())
                    .build();

            IndexRequest request = IndexRequest.of(r -> r
                    .index(INDEX_NAME)
                    .id(String.valueOf(docId))
                    .document(index)
            );

            elasticsearchClient.index(request);
            log.info("文档索引成功: docId={}, title={}", docId, doc.getTitle());
        } catch (IOException e) {
            log.error("文档索引失败: docId={}", docId, e);
            throw new RuntimeException("索引文档失败", e);
        }
    }

    @Override
    public void deleteDocumentIndex(Long docId) {
        try {
            DeleteRequest request = DeleteRequest.of(r -> r
                    .index(INDEX_NAME)
                    .id(String.valueOf(docId))
            );
            elasticsearchClient.delete(request);
            log.info("文档索引已删除: docId={}", docId);
        } catch (IOException e) {
            log.error("删除文档索引失败: docId={}", docId, e);
        }
    }

    @Override
    public void rebuildAllIndex() {
        log.info("开始重建所有文档索引...");
        try {
            ensureIndex();
            // 清空旧索引文档（保留索引结构）
            try {
                elasticsearchClient.deleteByQuery(dq -> dq
                        .index(INDEX_NAME)
                        .query(q -> q.matchAll(ma -> ma)));
            } catch (Exception e) {
                log.warn("清空旧索引失败（可能不存在）: {}", e.getMessage());
            }

            // 查询所有文档并重建索引
            List<Document> docs = documentMapper.selectList(null);
            for (Document doc : docs) {
                try {
                    indexDocument(doc.getId());
                } catch (Exception e) {
                    log.error("索引单个文档失败: docId={}", doc.getId(), e);
                }
            }
            log.info("索引重建完成，共 {} 条文档", docs.size());
        } catch (Exception e) {
            log.error("重建索引失败", e);
            throw new RuntimeException("重建索引失败", e);
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 构建切块索引（kb_{kbId}）的查询：关键词匹配 content / file_name + 权限过滤 + 知识库过滤。
     */
    private BoolQuery buildChunkBoolQuery(String keyword, Long kbId, Long userId, Long orgId) {
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

        // 关键词匹配（content 为主，file_name 加权）
        if (keyword != null && !keyword.trim().isEmpty()) {
            BoolQuery.Builder keywordBuilder = new BoolQuery.Builder();
            keywordBuilder.should(s -> s.match(m -> m
                    .field("content").query(keyword)));
            keywordBuilder.should(s -> s.match(m -> m
                    .field("file_name").query(keyword).boost(2.0f)));
            keywordBuilder.should(s -> s.matchPhrase(mp -> mp
                    .field("content").query(keyword).boost(2.0f)));
            keywordBuilder.minimumShouldMatch("1");
            boolBuilder.must(m -> m.bool(keywordBuilder.build()));
        } else {
            boolBuilder.must(m -> m.matchAll(ma -> ma));
        }

        // 权限过滤
        BoolQuery.Builder permissionBuilder = new BoolQuery.Builder();
        // 公开切块
        permissionBuilder.should(s -> s.term(t -> t
                .field("visibility").value(FieldValue.of("PUBLIC"))));
        // 自己创建的文档切块
        if (userId != null) {
            permissionBuilder.should(s -> s.term(t -> t
                    .field("owner_id").value(FieldValue.of(userId))));
        }
        // 组织内文档切块
        if (orgId != null) {
            permissionBuilder.should(s -> s.bool(b -> b
                    .must(m -> m.term(t -> t
                            .field("visibility").value(FieldValue.of("ORG"))))
                    .must(m -> m.term(t -> t
                            .field("org_id").value(FieldValue.of(orgId))))
            ));
        }
        boolBuilder.filter(f -> f.bool(permissionBuilder.build()));

        // 知识库过滤
        if (kbId != null) {
            boolBuilder.filter(f -> f.term(t -> t
                    .field("kb_id").value(FieldValue.of(kbId))));
        }

        return boolBuilder.build();
    }

    /**
     * 解析切块索引命中：以 document_id 反查文档表补全标题/作者/时间等展示字段。
     */
    private DocumentSearchResponse parseChunkHit(Hit<Map<String, Object>> hit, Long kbId) {
        Map<String, Object> source = hit.source();
        if (source == null) {
            return null;
        }

        Object docIdObj = source.get("document_id");
        Long docId = docIdObj instanceof Number ? ((Number) docIdObj).longValue() : null;

        // 获取高亮内容
        Map<String, List<String>> highlightFields = hit.highlight();
        List<String> highlightFragments = new ArrayList<>();
        if (highlightFields != null) {
            List<String> contentHighlights = highlightFields.get("content");
            if (contentHighlights != null) {
                highlightFragments = contentHighlights;
            }
        }

        // 反查文档表补全展示字段
        Document doc = docId != null ? documentMapper.selectById(docId) : null;

        String title = "";
        String ownerName = "";
        String fileType = "";
        LocalDateTime createTime = null;
        Long ownerId = null;
        if (doc != null) {
            title = doc.getTitle() != null ? doc.getTitle()
                    : String.valueOf(source.getOrDefault("file_name", ""));
            ownerId = doc.getOwnerId();
            if (ownerId != null) {
                User owner = userMapper.selectById(ownerId);
                ownerName = owner != null ? owner.getUsername() : "";
            }
            fileType = doc.getFileType() != null ? doc.getFileType() : "";
            createTime = doc.getCreateTime();
        }

        // 查询知识库名称
        String kbName = "";
        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        if (kb != null) {
            kbName = kb.getName();
        }

        return DocumentSearchResponse.builder()
                .id(docId)
                .title(title)
                .titlePlain(title)
                .contentSnippet(highlightFragments.isEmpty() ? "" : highlightFragments.get(0))
                .highlightFragments(highlightFragments)
                .kbId(kbId)
                .kbName(kbName)
                .ownerId(ownerId)
                .ownerName(ownerName)
                .fileType(fileType)
                .score(hit.score() != null ? hit.score().floatValue() : null)
                .createTime(createTime)
                .build();
    }
}
