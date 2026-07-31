package com.example.rag.service.export;

import com.example.rag.entity.Document;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

/**
 * PDF 文档导出策略
 *
 * <p>使用 Apache PDFBox 生成 PDF 文件。</p>
 *
 * @author knowledge-rag-team
 */
@Slf4j
@Component
public class PdfExportStrategy implements DocumentExportStrategy {

    @Override
    public ByteArrayOutputStream export(Document document) throws Exception {
        log.info("开始导出 PDF: docId={}, title={}", document.getId(), document.getTitle());

        try (PDDocument pdDocument = new PDDocument()) {
            // 加载中文字体（需要系统中存在字体文件）
            PDFont font = loadFont(pdDocument);

            // 创建页面
            PDPage page = new PDPage(PDRectangle.A4);
            pdDocument.addPage(page);

            // 写入内容
            try (PDPageContentStream contentStream = new PDPageContentStream(pdDocument, page)) {
                contentStream.setFont(font, 12);
                contentStream.beginText();
                contentStream.newLineAtOffset(50, 750);
                contentStream.setLeading(14.5f);

                // 写入标题
                contentStream.setFont(font, 18);
                contentStream.showText(document.getTitle() != null ? document.getTitle() : "无标题");
                contentStream.newLine();
                contentStream.newLine();

                // 写入内容
                contentStream.setFont(font, 12);
                String content = document.getContent();
                if (content != null && !content.isEmpty()) {
                    // 按行写入，处理自动换行
                    String[] lines = content.split("\n");
                    for (String line : lines) {
                        // 简单的字符截断处理（PDFBox 不支持自动换行）
                        int maxCharsPerLine = 70;
                        if (line.length() > maxCharsPerLine) {
                            int start = 0;
                            while (start < line.length()) {
                                int end = Math.min(start + maxCharsPerLine, line.length());
                                String subLine = line.substring(start, end);
                                contentStream.showText(subLine);
                                contentStream.newLine();
                                start = end;
                            }
                        } else {
                            contentStream.showText(line);
                            contentStream.newLine();
                        }
                    }
                } else {
                    contentStream.showText("（文档内容为空）");
                }

                contentStream.endText();
            }

            // 输出到字节流
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            pdDocument.save(outputStream);

            log.info("PDF 导出完成: docId={}, size={} bytes", document.getId(), outputStream.size());
            return outputStream;
        } catch (IOException e) {
            log.error("PDF 导出失败: docId={}", document.getId(), e);
            throw new RuntimeException("PDF 导出失败: " + e.getMessage(), e);
        }
    }

    /**
     * 加载字体，优先使用中文字体
     */
    private PDFont loadFont(PDDocument document) {
        try {
            // 尝试加载系统字体（Linux/Mac/Windows）
            String[] fontPaths = {
                    "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
                    "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
                    "/System/Library/Fonts/PingFang.ttc",
                    "C:/Windows/Fonts/msyh.ttc",
                    "C:/Windows/Fonts/simhei.ttf"
            };

            for (String fontPath : fontPaths) {
                File fontFile = new File(fontPath);
                if (fontFile.exists()) {
                    log.info("加载中文字体: {}", fontPath);
                    return PDType0Font.load(document, fontFile);
                }
            }

            // 回退到内置字体（不支持中文，但不会报错）
            log.warn("未找到中文字体，使用内置字体（中文可能无法正确显示）");
            return PDType1Font.HELVETICA;
        } catch (Exception e) {
            log.warn("加载字体失败: {}", e.getMessage());
            return PDType1Font.HELVETICA;
        }
    }

    @Override
    public String getFormatType() {
        return "pdf";
    }

    @Override
    public String getContentType() {
        return "application/pdf";
    }

    @Override
    public String getFileExtension() {
        return ".pdf";
    }
}
