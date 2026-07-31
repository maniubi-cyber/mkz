package com.example.rag.service.export;

import com.example.rag.entity.Document;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

/**
 * Markdown 文档导出策略
 *
 * <p>将文档导出为 Markdown 格式，保留原始内容和元数据。</p>
 *
 * @author knowledge-rag-team
 */
@Slf4j
@Component
public class MarkdownExportStrategy implements DocumentExportStrategy {

    @Override
    public ByteArrayOutputStream export(Document document) throws Exception {
        log.info("开始导出 Markdown: docId={}, title={}", document.getId(), document.getTitle());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
            // 写入 YAML Front Matter（元数据）
            writer.println("---");
            writer.println("title: " + (document.getTitle() != null ? document.getTitle() : "无标题"));
            writer.println("id: " + document.getId());
            writer.println("kbId: " + document.getKbId());
            writer.println("ownerId: " + document.getOwnerId());
            writer.println("visibility: " + document.getVisibility());
            writer.println("version: " + document.getVersion());
            writer.println("fileType: " + document.getFileType());
            if (document.getCreateTime() != null) {
                writer.println("createTime: " + document.getCreateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            }
            if (document.getUpdateTime() != null) {
                writer.println("updateTime: " + document.getUpdateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            }
            writer.println("---");
            writer.println();

            // 写入标题
            writer.println("# " + (document.getTitle() != null ? document.getTitle() : "无标题"));
            writer.println();

            // 写入内容
            String content = document.getContent();
            if (content != null && !content.isEmpty()) {
                writer.println(content);
            } else {
                writer.println("（文档内容为空）");
            }

            writer.flush();

            log.info("Markdown 导出完成: docId={}, size={} bytes", document.getId(), outputStream.size());
            return outputStream;
        } catch (Exception e) {
            log.error("Markdown 导出失败: docId={}", document.getId(), e);
            throw new RuntimeException("Markdown 导出失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getFormatType() {
        return "markdown";
    }

    @Override
    public String getContentType() {
        return "text/markdown; charset=UTF-8";
    }

    @Override
    public String getFileExtension() {
        return ".md";
    }
}
