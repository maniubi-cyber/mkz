package com.example.rag.service.impl;

import com.example.rag.entity.Document;
import com.example.rag.service.DocumentExportService;
import com.example.rag.service.export.DocumentExportFactory;
import com.example.rag.service.export.DocumentExportStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

/**
 * 文档导出服务实现 - 策略模式 + 工厂模式
 *
 * <p>文档导出支持 PDF、Word、Markdown 三种格式，
 * 使用策略模式将每种导出逻辑封装为独立策略类，
 * 通过工厂模式管理策略实例。</p>
 *
 * <h3>重构前后对比：</h3>
 * <pre>
 *   // 重构前：大量 if-else
 *   if ("pdf".equals(format)) {
 *       // PDF导出逻辑（50行）
 *   } else if ("word".equals(format)) {
 *       // Word导出逻辑（50行）
 *   } else if ("markdown".equals(format)) {
 *       // MD导出逻辑（30行）
 *   }
 *
 *   // 重构后：一行代码
 *   DocumentExportStrategy strategy = DocumentExportFactory.getStrategy(format);
 *   return strategy.export(document);
 * </pre>
 *
 * @author knowledge-rag-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentExportServiceImpl implements DocumentExportService {

    @Override
    public ByteArrayOutputStream exportDocument(Document document, String formatType) throws Exception {
        log.info("开始导出文档: docId={}, title={}, format={}",
                document.getId(), document.getTitle(), formatType);

        // 通过工厂获取对应的导出策略
        DocumentExportStrategy strategy = DocumentExportFactory.getStrategy(formatType);

        // 执行导出
        ByteArrayOutputStream outputStream = strategy.export(document);

        log.info("文档导出完成: docId={}, format={}, size={} bytes",
                document.getId(), formatType, outputStream.size());

        return outputStream;
    }

    @Override
    public String getExportFilename(Document document, String formatType) {
        DocumentExportStrategy strategy = DocumentExportFactory.getStrategy(formatType);

        String baseName = document.getTitle() != null ?
                document.getTitle().replaceAll("[\\\\/:*?\"<>|]", "_") :
                "document_" + document.getId();

        return baseName + strategy.getFileExtension();
    }
}
