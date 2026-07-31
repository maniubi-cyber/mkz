package com.example.rag.service.export;

import com.example.rag.entity.Document;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;

/**
 * Word 文档导出策略
 *
 * <p>使用 Apache POI 生成 .docx 格式文件。</p>
 *
 * @author knowledge-rag-team
 */
@Slf4j
@Component
public class WordExportStrategy implements DocumentExportStrategy {

    @Override
    public ByteArrayOutputStream export(Document document) throws Exception {
        log.info("开始导出 Word: docId={}, title={}", document.getId(), document.getTitle());

        try (XWPFDocument doc = new XWPFDocument()) {
            // 添加标题
            XWPFParagraph titleParagraph = doc.createParagraph();
            titleParagraph.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
            XWPFRun titleRun = titleParagraph.createRun();
            titleRun.setText(document.getTitle() != null ? document.getTitle() : "无标题");
            titleRun.setBold(true);
            titleRun.setFontSize(20);
            titleRun.setFontFamily("微软雅黑");

            // 添加分隔线
            XWPFParagraph separator = doc.createParagraph();
            separator.setBorderBottom(org.apache.poi.xwpf.usermodel.Borders.SINGLE);

            // 添加内容
            String content = document.getContent();
            if (content != null && !content.isEmpty()) {
                String[] lines = content.split("\n");
                for (String line : lines) {
                    XWPFParagraph paragraph = doc.createParagraph();
                    XWPFRun run = paragraph.createRun();
                    run.setText(line.isEmpty() ? " " : line);
                    run.setFontSize(12);
                    run.setFontFamily("宋体");
                }
            } else {
                XWPFParagraph emptyParagraph = doc.createParagraph();
                XWPFRun emptyRun = emptyParagraph.createRun();
                emptyRun.setText("（文档内容为空）");
                emptyRun.setItalic(true);
                emptyRun.setColor("999999");
            }

            // 输出到字节流
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            doc.write(outputStream);

            log.info("Word 导出完成: docId={}, size={} bytes", document.getId(), outputStream.size());
            return outputStream;
        } catch (Exception e) {
            log.error("Word 导出失败: docId={}", document.getId(), e);
            throw new RuntimeException("Word 导出失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getFormatType() {
        return "word";
    }

    @Override
    public String getContentType() {
        return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    }

    @Override
    public String getFileExtension() {
        return ".docx";
    }
}
