package com.example.rag.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Python AI 服务 Feign Client（声明式 HTTP 调用）
 *
 * <p>替代 RestTemplate，使用 OpenFeign 实现声明式服务调用。
 * 配合 Nacos 服务发现，自动负载均衡。
 *
 * <h3>Python 服务 API</h3>
 * <ul>
 *   <li>POST /api/parse — 文档解析（文本提取 → 切片 → 向量化 → 写入 Chroma）</li>
 *   <li>POST /api/vectors/delete — 删除文档向量</li>
 * </ul>
 *
 * @author knowledge-rag团队
 */
@FeignClient(
        name = "ai-service",
        url = "${ai-service.base-url:http://localhost:8000}",
        configuration = AiServiceClientConfig.class
)
public interface AiServiceClient {

    /**
     * 触发文档解析
     *
     * <p>Python 服务负责：
     * <ol>
     *   <li>从 MinIO 下载文件</li>
     *   <li>文本提取（PDF/DOCX/XLSX/TXT/MD）</li>
     *   <li>文本切片（按段落/句子 + 重叠窗口）</li>
     *   <li>向量化（调用 Embedding 模型）</li>
     *   <li>写入向量库（Chroma）</li>
     *   <li>写入 document_chunk 表（MySQL）</li>
     * </ol>
     *
     * @param request 请求参数 { doc_id, minio_path, file_type, kb_id }
     * @return 响应 { chunk_count, error? }
     */
    @PostMapping("/api/parse")
    Map<String, Object> parseDocument(@RequestBody Map<String, Object> request);

    /**
     * 删除文档向量
     *
     * @param request 请求参数 { doc_id, kb_id }
     * @return 响应 { success, message? }
     */
    @PostMapping("/api/vectors/delete")
    Map<String, Object> deleteVectors(@RequestBody Map<String, Object> request);
}
