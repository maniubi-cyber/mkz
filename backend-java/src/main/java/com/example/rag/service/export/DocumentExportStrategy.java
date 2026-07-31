package com.example.rag.service.export;

import com.example.rag.entity.Document;

import java.io.ByteArrayOutputStream;

/**
 * 文档导出策略接口 - 策略模式
 *
 * <p>文档导出支持 PDF、Word、Markdown 三种格式，
 * 使用策略模式将每种导出逻辑封装为独立策略类，
 * 新增格式只需添加类，代码复杂度显著降低，可拓展性增强。</p>
 *
 * <h3>策略模式结构：</h3>
 * <pre>
 *   DocumentExportStrategy (接口)
 *       ├── PdfExportStrategy (PDF导出)
 *       ├── WordExportStrategy (Word导出)
 *       └── MarkdownExportStrategy (Markdown导出)
 * </pre>
 *
 * <h3>优势：</h3>
 * <ul>
 *   <li>消除 if-else 判断，代码更清晰</li>
 *   <li>每种导出逻辑独立封装，易于维护</li>
 *   <li>新增格式只需添加新策略类，符合开闭原则</li>
 * </ul>
 *
 * @author knowledge-rag-team
 */
public interface DocumentExportStrategy {

    /**
     * 导出文档
     *
     * @param document 文档实体
     * @return 导出文件的字节流
     * @throws Exception 导出异常
     */
    ByteArrayOutputStream export(Document document) throws Exception;

    /**
     * 获取导出格式类型
     *
     * @return 格式类型（pdf / word / markdown）
     */
    String getFormatType();

    /**
     * 获取导出文件的 Content-Type
     *
     * @return MIME 类型
     */
    String getContentType();

    /**
     * 获取导出文件扩展名
     *
     * @return 扩展名（如 .pdf / .docx / .md）
     */
    String getFileExtension();
}
