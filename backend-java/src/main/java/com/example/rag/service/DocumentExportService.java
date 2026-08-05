package com.example.rag.service;

import com.example.rag.entity.Document;
import com.example.rag.service.export.DocumentExportFactory;

import java.io.ByteArrayOutputStream;

/**
 * 文档导出服务
 *
 * <p>使用策略模式 + 工厂模式实现文档导出（PDF/Word/Markdown）。</p>
 *
 * @author knowledge-rag-team
 */
public interface DocumentExportService {

    /**
     * 导出文档为指定格式
     *
     * @param document   文档实体
     * @param formatType 导出格式（pdf / word / markdown）
     * @return 导出的文件字节流
     * @throws Exception 导出异常
     */
    ByteArrayOutputStream exportDocument(Document document, String formatType) throws Exception;

    /**
     * 获取导出文件的文件名
     *
     * @param document   文档实体
     * @param formatType 导出格式
     * @return 文件名
     */
    String getExportFilename(Document document, String formatType);

    /**
     * 获取导出文件的 Content-Type
     *
     * @param formatType 导出格式（pdf / word / markdown）
     * @return MIME 类型
     */
    static String getContentType(String formatType) {
        return DocumentExportFactory.getStrategy(formatType).getContentType();
    }
}
