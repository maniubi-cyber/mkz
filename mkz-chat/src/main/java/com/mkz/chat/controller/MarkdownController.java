package com.mkz.chat.controller;

import com.mkz.chat.domain.po.MarkdownDocs;
import com.mkz.chat.feign.AiBridgeClient;
import com.mkz.chat.service.IMarkdownDocsService;
import com.mkz.common.annotations.NoWrapper;
import com.mkz.common.domain.R;
import com.mkz.common.domain.dto.PageDTO;
import com.mkz.common.domain.query.PageQuery;
import com.mkz.common.utils.UserContext;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 知识库接口（薄网关层）。
 * <p>
 * AI 能力（向量切割、Embedding、RAG 检索与对话）通过 Feign 调用 mkz-ai-bridge 桥接服务。
 * 本地仅保留知识库元数据的分页查询、内容查看与更新（操作 mkz_chat 库）。
 * </p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/file")
@Api(tags = "知识库接口")
public class MarkdownController {

    private final IMarkdownDocsService markdownDocsService;
    private final AiBridgeClient aiBridgeClient;

    @NoWrapper
    @ApiOperation("上传文件到知识库（委托桥接服务进行向量化与入库）")
    @PostMapping("/upload")
    public R<Map<String, Object>> uploadMarkdown(@RequestParam MultipartFile file,
                                                 @RequestParam(value = "docId", required = false) String docId) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new RuntimeException("请先登录");
        }
        if (docId == null || docId.isEmpty()) {
            docId = "doc-" + userId + "-" + System.currentTimeMillis();
        }
        return aiBridgeClient.uploadMarkdown(file, userId.toString(), docId);
    }

    @NoWrapper
    @ApiOperation("根据知识库内容对话（委托桥接服务 RAG）")
    @GetMapping("/chat")
    public R<Map<String, Object>> chatByMarkdownDoc(@RequestParam String message,
                                                    @RequestParam(value = "sessionId", defaultValue = "1") String sessionId) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new RuntimeException("请先登录");
        }
        return aiBridgeClient.knowledgeChat(message, sessionId, userId.toString());
    }

    @ApiOperation("分页查询用户知识库文件列表")
    @GetMapping("/page")
    public PageDTO<MarkdownDocs> queryMarkdownPage(PageQuery query) {
        return markdownDocsService.queryMarkdownPage(query);
    }

    @ApiOperation("根据文件id查看文件内容")
    @GetMapping("/{id}")
    public String getMarkdown(@PathVariable("id") Long fileId) {
        return markdownDocsService.getMarkdown(fileId);
    }

    @ApiOperation("更新文件内容")
    @PutMapping("/update")
    public void updateMarkdown(@RequestBody MarkdownDocs markdownDocs) {
        markdownDocsService.updateMarkdown(markdownDocs);
    }

    @NoWrapper
    @ApiOperation("根据文件id删除文件（委托桥接服务删除向量与文档）")
    @DeleteMapping("/{id}")
    public R<Map<String, Object>> deleteMarkdown(@PathVariable("id") Long fileId) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new RuntimeException("请先登录");
        }
        return aiBridgeClient.deleteDoc(fileId.toString(), userId.toString());
    }
}
