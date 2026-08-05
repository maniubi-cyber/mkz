package com.example.rag.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Python AI 服务 HTTP 客户端（RestTemplate 实现）
 *
 * <p>Java 后端通过 RestTemplate 调用 Python FastAPI 服务，
 * 完成 RAG 文档解析、向量删除等 AI 能力对接。
 *
 * <h3>Python 服务 API</h3>
 * <ul>
 *   <li>POST /ai/documents/parse         — 文档解析（父子切块 → LLM 元数据 → Qdrant + Redis + ES BM25）</li>
 *   <li>POST /ai/documents/vectors/delete — 删除文档索引（Qdrant + Redis + ES BM25）</li>
 *   <li>POST /ai/documents/rebuild        — 增量重建（版本号定位，缓解全量重构 token 消耗）</li>
 * </ul>
 *
 * @author knowledge-rag团队
 */
@Slf4j
@Component
public class AiServiceClient {

    private final RestTemplate restTemplate;

    @Value("${ai-service.base-url:http://localhost:8000}")
    private String baseUrl;

    public AiServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 触发文档解析
     *
     * <p>Python 服务负责：
     * <ol>
     *   <li>从 MinIO 下载文件</li>
     *   <li>文本提取（PDF/DOCX/XLSX/TXT/MD）</li>
     *   <li>父子切块（父块段落级 / 子块句子级）</li>
     *   <li>LLM 自动提取元数据（topic / keywords）</li>
     *   <li>父块写入 Redis（回溯用）</li>
     *   <li>子块 embedding 写入 Qdrant（检索入口）</li>
     *   <li>子块写入 ES BM25 索引（IK 中文分词，双路召回）</li>
     * </ol>
     *
     * @param request 请求参数 { doc_id, minio_path, file_type, kb_id, ... }
     * @return 响应 { chunk_count, parent_chunks, child_chunks, ... }
     */
    public Map<String, Object> parseDocument(Map<String, Object> request) {
        String url = baseUrl + "/ai/documents/parse";
        return postForMap(url, request);
    }

    /**
     * 删除文档索引数据
     *
     * @param request 请求参数 { doc_id, kb_id }
     * @return 响应 { deleted_child_vectors, deleted_parent_chunks, ... }
     */
    public Map<String, Object> deleteVectors(Map<String, Object> request) {
        String url = baseUrl + "/ai/documents/vectors/delete";
        return postForMap(url, request);
    }

    /**
     * 增量重建文档索引（编辑场景，按版本号定位）
     *
     * @param request 请求参数 { doc_id, minio_path, file_type, kb_id, ... }
     * @return 响应 { old_version, new_version, parent_chunks, child_chunks, ... }
     */
    public Map<String, Object> rebuildDocument(Map<String, Object> request) {
        String url = baseUrl + "/ai/documents/rebuild";
        return postForMap(url, request);
    }

    /**
     * RAG 对话（检索 + LLM 生成）
     *
     * @param request 请求参数 { kb_id, question, conversation_id, history, user_id, role, org_id, ... }
     * @return 响应 { answer, sources, token_usage, generation_time_ms, ... }
     */
    public Map<String, Object> chat(Map<String, Object> request) {
        String url = baseUrl + "/ai/chat";
        return postForMap(url, request);
    }

    /**
     * 统一 POST 调用：JSON 请求体 → Map 响应
     */
    private Map<String, Object> postForMap(String url, Map<String, Object> request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
        try {
            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);
            log.debug("AI 服务调用成功: url={}, response={}", url, response);
            return response;
        } catch (Exception e) {
            log.error("AI 服务调用失败: url={}, error={}", url, e.getMessage());
            throw new RuntimeException("AI 服务调用失败: " + e.getMessage(), e);
        }
    }
}