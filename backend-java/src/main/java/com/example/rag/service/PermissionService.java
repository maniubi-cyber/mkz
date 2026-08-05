package com.example.rag.service;

import com.example.rag.entity.Document;
import com.example.rag.entity.KnowledgeBase;

/**
 * 权限校验服务 —— 统一收口文档 / 知识库的可见性与操作权限判断。
 *
 * <p>原先散落在 DocumentServiceImpl 与 KnowledgeBaseServiceImpl 中的
 * 重复判断（owner / PUBLIC / ORG 三路逻辑）统一迁移至此，避免两处漂移。</p>
 *
 * @author knowledge-rag-team
 */
public interface PermissionService {

    /**
     * 校验可见范围合法性（PRIVATE / PUBLIC / ORG；ORG 时 orgId 必填）。
     */
    void validateVisibility(String visibility, Long orgId);

    /**
     * 上传权限：admin / owner / PUBLIC / 同组织 ORG 成员可向知识库上传文档。
     */
    void checkKbUploadPermission(KnowledgeBase kb);

    /**
     * 查看权限：admin / owner / PUBLIC / 同组织 ORG 成员可查看知识库。
     */
    void checkKbViewPermission(KnowledgeBase kb);

    /**
     * 编辑 / 删除知识库：仅 owner 或 admin。
     */
    void checkKbOwnerOrAdmin(KnowledgeBase kb, String action);

    /**
     * 文档查看权限：admin / owner / PUBLIC / 同组织 ORG 成员可查看文档。
     */
    void checkDocViewPermission(Document doc);

    /**
     * 文档写权限：admin / owner / document_permission 表中授予 WRITE 或 ADMIN 的用户。
     * 协同编辑（WebSocket 会话 + 全文保存）以此为准。
     */
    void checkDocWritePermission(Document doc);

    /**
     * 文档管理操作（删除等）：仅 owner 或 admin。
     */
    void checkOwnerOrAdmin(Document doc, String action);
}
