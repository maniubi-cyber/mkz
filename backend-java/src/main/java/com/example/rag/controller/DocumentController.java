package com.example.rag.controller;

import com.example.rag.common.Result;
import com.example.rag.dto.request.DocumentContentUpdateRequest;
import com.example.rag.dto.response.DocumentDetailResponse;
import com.example.rag.dto.response.DocumentResponse;
import com.example.rag.dto.response.PageResponse;
import com.example.rag.service.DocumentAsyncService;
import com.example.rag.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.ExecutionException;

/**
 * 文档管理接口
 *
 * <p>提供文档上传、分页查询、详情查看、删除、重新解析等操作。
 * 上传文件需经过 Magic Number 校验 + MD5 去重 + MinIO 存储。</p>
 *
 * @author knowledge-rag-team
 */
@Slf4j
@Tag(name = "文档管理", description = "文档上传 / 分页查询 / 详情 / 删除 / 重新解析")
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentAsyncService documentAsyncService;

    // ==================== 上传文档 ====================

    @Operation(
            summary = "上传文档（multipart/form-data）",
            description = """
                    上传文档到指定知识库。处理流程：
                    <ol>
                      <li>Magic Number 魔数校验 —— 防止扩展名伪造</li>
                      <li>文件大小校验 —— 限制 ≤ 50MB</li>
                      <li>文件名安全处理 —— 防目录穿越 / 非法字符过滤</li>
                      <li>MD5 计算并去重 —— 同一 KB 下相同文件秒传</li>
                      <li>MinIO 对象存储上传</li>
                      <li>MySQL 元数据保存（parse_status = PENDING）</li>
                      <li>Redis 缓存写入</li>
                      <li>异步触发 Python AI 解析</li>
                    </ol>
                    支持的文件类型：PDF、DOCX、XLSX、TXT、MD
                    """
    )
    @PostMapping("/upload")
    public Result<DocumentResponse> upload(
            @Parameter(description = "上传文件（≤50MB）", required = true)
            @RequestParam("file") @NotNull MultipartFile file,

            @Parameter(description = "目标知识库 ID", required = true, example = "1")
            @RequestParam("kbId") @NotNull Long kbId,

            @Parameter(description = "可见范围：PRIVATE / PUBLIC / ORG（可选，默认继承知识库）",
                    example = "PRIVATE")
            @RequestParam(value = "visibility", required = false) String visibility,

            @Parameter(description = "组织 ID（visibility = ORG 时必填）", example = "10")
            @RequestParam(value = "orgId", required = false) Long orgId) {

        DocumentResponse doc = documentService.upload(file, kbId, visibility, orgId);
        return Result.success(doc);
    }

    // ==================== 分页查询 ====================

    @Operation(
            summary = "分页查询知识库下的文档",
            description = """
                    根据权限过滤 + 可选文件名模糊搜索，分页返回文档列表。
                    权限规则：
                    <ul>
                      <li>ADMIN —— 查看知识库下所有文档</li>
                      <li>普通用户 —— 仅查看自己上传 / PUBLIC / 同组织 ORG 的文档</li>
                    </ul>
                    每条记录包含预签名下载 URL（7 天有效）。
                    """
    )
    @GetMapping
    public Result<PageResponse<DocumentResponse>> list(
            @Parameter(description = "知识库 ID", required = true, example = "1")
            @RequestParam("kbId") @NotNull Long kbId,

            @Parameter(description = "页码（从 1 开始）", example = "1")
            @RequestParam(value = "page", defaultValue = "1") int page,

            @Parameter(description = "每页条数", example = "20")
            @RequestParam(value = "size", defaultValue = "20") int size,

            @Parameter(description = "文件名搜索关键词（模糊匹配，可选）", example = "设计文档")
            @RequestParam(value = "keyword", required = false) String keyword) {

        PageResponse<DocumentResponse> docs = documentService.listByKb(kbId, page, size, keyword);
        return Result.success(docs);
    }

    // ==================== 获取文档详情 ====================

    @Operation(
            summary = "获取文档详情",
            description = """
                    返回文档完整信息，包含：
                    <ul>
                      <li>基本元数据（文件名、大小、类型、MD5）</li>
                      <li>chunk_count —— 切片数量</li>
                      <li>parse_status —— 解析状态（PENDING / PARSING / SUCCESS / FAILED）</li>
                      <li>parse_fail_msg —— 解析失败原因（如有）</li>
                      <li>download_url —— MinIO 预签名下载链接（7 天有效）</li>
                    </ul>
                    优先从 Redis 缓存读取，缓存未命中时查询 MySQL。
                    """
    )
    @GetMapping("/{id}")
    public Result<DocumentResponse> getById(
            @Parameter(description = "文档 ID", required = true, example = "1")
            @PathVariable Long id) {

        DocumentResponse doc = documentService.getById(id);
        return Result.success(doc);
    }

    // ==================== 文档详情（聚合，含正文） ====================

    @Operation(
            summary = "获取文档详情（多源聚合，含正文）",
            description = """
                    返回完整的文档详情，包含：
                    <ul>
                      <li>正文 content（Markdown）/ contentHtml</li>
                      <li>作者信息、权限列表、版本历史</li>
                      <li>浏览数（每次访问原子自增）</li>
                    </ul>
                    前端协同编辑器的初始内容与版本号均来源于此接口。
                    """
    )
    @GetMapping("/{id}/detail")
    public Result<DocumentDetailResponse> getDetail(
            @Parameter(description = "文档 ID", required = true, example = "1")
            @PathVariable Long id) {
        try {
            DocumentDetailResponse detail = documentAsyncService.getDocumentDetailAsync(id);
            if (detail == null) {
                return Result.error(404, "文档不存在");
            }
            return Result.success(detail);
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取文档详情失败: docId={}", id, e);
            return Result.error(500, "获取文档详情失败");
        }
    }

    // ==================== 删除文档 ====================

    @Operation(
            summary = "删除文档（软删除 + 异步资源清理）",
            description = """
                    完整删除流程：
                    <ol>
                      <li>权限校验 —— 仅文档上传者或 ADMIN 可删除</li>
                      <li>MySQL 软删除 —— 标记 is_deleted = 1（数据可恢复）</li>
                      <li>Redis 缓存失效 —— 删除文档详情缓存 + 知识库列表分页缓存</li>
                      <li>异步清理（不阻塞响应）：
                        <ul>
                          <li>MinIO 文件删除</li>
                          <li>document_chunk 切片数据删除</li>
                          <li>向量库向量删除（调用 Python AI 服务）</li>
                        </ul>
                      </li>
                    </ol>
                    """
    )
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "文档 ID", required = true, example = "1")
            @PathVariable Long id) {

        documentService.delete(id);
        return Result.success();
    }

    // ==================== 重新解析 ====================

    @Operation(
            summary = "重新解析文档",
            description = """
                    重新触发文档解析流程。处理逻辑：
                    <ol>
                      <li>权限校验</li>
                      <li>状态机校验 —— PARSING 状态拒绝重复触发（返回 400）</li>
                      <li>清理旧数据 —— 删除向量库中的旧向量 + 删除旧的 document_chunk 记录</li>
                      <li>重置状态 —— parse_status = PENDING, chunk_count = 0</li>
                      <li>更新 Redis 缓存</li>
                      <li>异步触发 Python AI 解析</li>
                    </ol>
                    状态流转：PENDING/FAILED/SUCCESS → PENDING → PARSING → SUCCESS/FAILED
                    """
    )
    @PostMapping("/{id}/reparse")
    public Result<DocumentResponse> reparse(
            @Parameter(description = "文档 ID", required = true, example = "1")
            @PathVariable Long id) {

        DocumentResponse doc = documentService.reparse(id);
        return Result.success("重新解析已触发", doc);
    }

    // ==================== 协同编辑内容保存 ====================

    @Operation(
            summary = "保存协同编辑后的文档正文",
            description = """
                    前端在协同收敛后，把当前权威全文与所基于的 OT 版本号提交保存。
                    采用 last-write-wins 覆盖正文，并落一条版本历史记录。
                    """
    )
    @PutMapping("/{id}/content")
    public Result<Void> saveContent(
            @Parameter(description = "文档 ID", required = true, example = "1")
            @PathVariable Long id,

            @Parameter(description = "保存请求体")
            @RequestBody DocumentContentUpdateRequest req) {

        documentService.updateContent(id, req.getContent(), req.getBaseRevision());
        return Result.success();
    }

    // ==================== 文档导出 ====================

    @Operation(
            summary = "导出文档",
            description = """
                    将文档导出为指定格式。
                    支持格式：pdf / word / markdown
                    使用策略模式 + 工厂模式，新增格式无需修改业务代码。
                    """
    )
    @GetMapping("/{id}/export")
    public void exportDocument(
            @Parameter(description = "文档 ID", required = true, example = "1")
            @PathVariable Long id,

            @Parameter(description = "导出格式：pdf / word / markdown", required = true, example = "pdf")
            @RequestParam("format") String format,

            jakarta.servlet.http.HttpServletResponse response
    ) throws Exception {
        java.io.ByteArrayOutputStream outputStream = documentService.exportDocument(id, format);
        DocumentResponse doc = documentService.getById(id);

        String filename = documentService.getExportFilename(doc, format);
        String contentType = documentService.getExportContentType(format);

        response.setContentType(contentType);
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + java.net.URLEncoder.encode(filename, "UTF-8") + "\"");
        response.setContentLength(outputStream.size());
        outputStream.writeTo(response.getOutputStream());
        outputStream.close();
    }
}
