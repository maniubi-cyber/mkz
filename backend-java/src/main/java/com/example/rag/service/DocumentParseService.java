package com.example.rag.service;

import com.example.rag.client.AiServiceClient;
import com.example.rag.entity.Document;
import com.example.rag.event.DocumentParseTriggerEvent;
import com.example.rag.mapper.DocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 文档解析服务（异步）
 *
 * <p>异步调用 Python AI 服务进行文档处理：
 * <ul>
 *   <li>/ai/documents/parse — 文档解析（父子切块 → LLM 元数据 → Qdrant + Redis + ES BM25）</li>
 *   <li>/ai/documents/vectors/delete — 删除文档索引（Qdrant + Redis + ES BM25）</li>
 * </ul>
 *
 * <h3>parse_status 状态机</h3>
 * <pre>
 * PENDING → PARSING → SUCCESS
 *                   → FAILED (记录 parse_fail_msg)
 * </pre>
 *
 * <h3>异步边界约定</h3>
 * <ul>
 *   <li>upload / reparse 事务内只发布 {@link DocumentParseTriggerEvent}，
 *       本服务在事务提交（AFTER_COMMIT）后真正触发解析，
 *       避免异步线程读到未提交的文档数据。</li>
 *   <li>解析调用本身带 {@code @Retryable} 重试（3 次退避），
 *       且通过 self 代理调用保证重试注解真正生效（同类自调用会绕过代理）。</li>
 *   <li>重试耗尽后由外层标记 FAILED，不向上抛异常。</li>
 * </ul>
 *
 * @author knowledge-rag团队
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentParseService {

    private final DocumentMapper documentMapper;
    private final AiServiceClient aiServiceClient;

    /** 自引用（Lazy）：让 @Async / @Retryable 注解经代理生效，避免同类自调用失效 */
    @Lazy
    private final DocumentParseService self;

    // ==================== 事件监听：事务提交后触发解析 ====================

    /**
     * 事务提交后异步触发文档解析。
     *
     * <p>fallbackExecution=true：无事务上下文（如定时任务直接发布事件）时也执行。</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onParseTriggered(DocumentParseTriggerEvent event) {
        self.triggerParseAsync(event.getDocId());
    }

    // ==================== 异步解析 ====================

    /**
     * 异步触发文档解析
     *
     * <p>状态流转：PENDING → PARSING → (SUCCESS | FAILED)</p>
     *
     * @param docId 文档 ID
     */
    @Async
    public void triggerParseAsync(Long docId) {
        log.info("开始异步解析文档: docId={}", docId);

        Document doc = documentMapper.selectById(docId);
        if (doc == null || doc.getIsDeleted() == 1) {
            log.warn("异步解析失败：文档不存在或已删除 docId={}", docId);
            return;
        }

        // 1. 更新状态为 PARSING
        doc.setParseStatus("PARSING");
        doc.setParseFailMsg(null);
        doc.setUpdateTime(LocalDateTime.now());
        documentMapper.updateById(doc);

        // 2. 调用 Python AI 解析服务（自带 3 次重试，经代理调用保证 @Retryable 生效）
        try {
            self.parseWithRetry(doc);

            // 3. 解析成功
            doc.setParseStatus("SUCCESS");
            doc.setParseFailMsg(null);
            doc.setUpdateTime(LocalDateTime.now());
            documentMapper.updateById(doc);
            log.info("文档解析成功: docId={}, fileName={}, chunkCount={}",
                    doc.getId(), doc.getFileName(), doc.getChunkCount());
        } catch (Exception e) {
            // 4. 重试耗尽后标记解析失败
            String failMsg = e.getMessage() != null ? e.getMessage() : "未知解析错误";
            if (failMsg.length() > 500) {
                failMsg = failMsg.substring(0, 500);
            }
            doc.setParseStatus("FAILED");
            doc.setParseFailMsg(failMsg);
            doc.setUpdateTime(LocalDateTime.now());
            documentMapper.updateById(doc);
            log.error("文档解析失败: docId={}, fileName={}, error={}",
                    doc.getId(), doc.getFileName(), failMsg);
        }
    }

    /**
     * 带重试的解析调用（独立方法保证 @Retryable 代理生效）。
     *
     * <p>注意：本方法只负责调用 Python 服务与回写 chunk_count，
     * 重试耗尽后抛出的异常由外层 {@link #triggerParseAsync} 捕获并标记 FAILED。</p>
     */
    @Retryable(
            retryFor = {RuntimeException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void parseWithRetry(Document doc) {
        callPythonParser(doc);
    }

    // ==================== 异步删除向量 ====================

    /**
     * 异步删除向量库中指定文档的所有向量
     *
     * <p>调用 Python AI 服务 POST /ai/documents/vectors/delete</p>
     *
     * @param docId 文档 ID
     * @param kbId  知识库 ID
     */
    @Async
    public void deleteVectorsAsync(Long docId, Long kbId) {
        log.info("开始异步删除向量: docId={}, kbId={}", docId, kbId);

        try {
            Map<String, Object> requestBody = Map.of(
                    "doc_id", docId,
                    "kb_id", kbId
            );

            log.debug("调用向量删除接口: body={}", requestBody);

            Map<String, Object> response = aiServiceClient.deleteVectors(requestBody);

            if (response != null) {
                log.info("向量删除成功: docId={}, response={}", docId, response);
            } else {
                log.warn("向量删除接口返回空: docId={}", docId);
            }
        } catch (Exception e) {
            log.error("向量删除失败: docId={}, kbId={}, error={}",
                    docId, kbId, e.getMessage());
            // 向量删除失败不抛异常，避免影响主流程
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 调用 Python AI 解析服务（使用 RestTemplate）
     *
     * <p>POST /api/parse
     * Body: { doc_id, minio_path, file_type, kb_id }
     *
     * <p>Python 服务负责：
     * <ol>
     *   <li>从 MinIO 下载文件</li>
     *   <li>文本提取（PDF/DOCX/XLSX/TXT/MD）</li>
     *   <li>文本切片（按段落/句子 + 重叠窗口）</li>
     *   <li>向量化（调用 Embedding 模型）</li>
     *   <li>子块 embedding 写入 Qdrant + 父块入 Redis + ES BM25</li>
     *   <li>写入 document_chunk 表（MySQL）</li>
     *   <li>回写 chunk_count 到 document 表</li>
     * </ol>
     */
    private void callPythonParser(Document doc) {
        Map<String, Object> requestBody = Map.of(
                "doc_id", doc.getId(),
                "minio_path", doc.getMinioPath(),
                "file_type", doc.getFileType(),
                "kb_id", doc.getKbId()
        );

        log.debug("调用 AI 解析服务: body={}", requestBody);

        try {
            // 使用 RestTemplate 调用
            Map<String, Object> response = aiServiceClient.parseDocument(requestBody);
            log.info("AI 解析服务响应: {}", response);

            // Python 服务返回 chunk_count，回写到文档
            if (response != null && response.containsKey("chunk_count")) {
                Object chunkCount = response.get("chunk_count");
                if (chunkCount instanceof Number) {
                    doc.setChunkCount(((Number) chunkCount).intValue());
                    documentMapper.updateById(doc);
                }
            }

            // 检查是否有错误
            if (response != null && response.containsKey("error")) {
                throw new RuntimeException("AI 解析服务返回错误: " + response.get("error"));
            }
        } catch (Exception e) {
            log.error("调用 AI 解析服务失败: error={}", e.getMessage());
            throw new RuntimeException("AI 解析服务调用失败: " + e.getMessage(), e);
        }
    }
}
