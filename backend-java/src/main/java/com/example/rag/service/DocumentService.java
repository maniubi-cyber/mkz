package com.example.rag.service;

import com.example.rag.dto.response.DocumentResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.Map;

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
    Map<String, Object> listByKb(Long kbId, int page, int size, String keyword);

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
}
