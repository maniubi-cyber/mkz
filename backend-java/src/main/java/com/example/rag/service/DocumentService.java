package com.example.rag.service;

import com.example.rag.dto.response.DocumentResponse;
import com.example.rag.dto.response.PageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;

/**
 * 文档服务接口
 *
 * @author knowledge-rag-team
 */
public interface DocumentService {

    /**
     * 上传文档
     */
    DocumentResponse upload(MultipartFile file, Long kbId, String visibility, Long orgId);

    /**
     * 分页列出知识库下的文档
     */
    PageResponse<DocumentResponse> listByKb(Long kbId, int page, int size, String keyword);

    /**
     * 获取文档详情
     */
    DocumentResponse getById(Long docId);

    /**
     * 删除文档
     */
    void delete(Long docId);

    /**
     * 重新解析文档
     */
    DocumentResponse reparse(Long docId);

    /**
     * 导出文档
     *
     * @param docId     文档 ID
     * @param formatType 导出格式（pdf / word / markdown）
     * @return 导出文件字节流
     */
    ByteArrayOutputStream exportDocument(Long docId, String formatType) throws Exception;

    /**
     * 保存协同编辑后的文档正文
     *
     * <p>协同编辑场景中，前端在收到自身 ack 并收敛后持有权威全文。
     * 保存采用 last-write-wins：用 UpdateWrapper 直接覆盖正文，
     * 绕过实体 @Version 乐观锁，避免并发保存互相拒绝；并落一条版本历史。</p>
     *
     * @param docId        文档 ID
     * @param content      收敛后的正文（Markdown）
     * @param baseRevision 客户端所基于的 OT 版本号（仅作记录）
     */
    void updateContent(Long docId, String content, Integer baseRevision);

    /**
     * 获取导出文件名
     */
    String getExportFilename(DocumentResponse doc, String formatType);

    /**
     * 获取导出文件的 Content-Type
     */
    String getExportContentType(String formatType);
}
