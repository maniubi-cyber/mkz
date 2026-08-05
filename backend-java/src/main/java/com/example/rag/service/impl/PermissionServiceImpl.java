package com.example.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.rag.common.BusinessException;
import com.example.rag.common.SecurityUtils;
import com.example.rag.entity.Document;
import com.example.rag.entity.DocumentPermission;
import com.example.rag.entity.KnowledgeBase;
import com.example.rag.mapper.DocumentPermissionMapper;
import com.example.rag.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 权限校验服务实现。
 *
 * <p>核心规则（文档与知识库一致）：</p>
 * <pre>
 *   可见性：
 *     admin 全见
 *     OR owner_id = 当前用户
 *     OR visibility = 'PUBLIC'
 *     OR (visibility = 'ORG' AND org_id = 当前用户 org_id)
 *
 *   写权限（协同编辑）：
 *     admin / owner / document_permission 中 WRITE 或 ADMIN
 * </pre>
 *
 * @author knowledge-rag-team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private static final String VISIBILITY_PUBLIC = "PUBLIC";
    private static final String VISIBILITY_ORG = "ORG";

    private static final String PERMISSION_WRITE = "WRITE";
    private static final String PERMISSION_ADMIN = "ADMIN";

    private final DocumentPermissionMapper documentPermissionMapper;

    @Override
    public void validateVisibility(String visibility, Long orgId) {
        String upper = visibility != null ? visibility.toUpperCase() : "";
        if (!"PRIVATE".equals(upper) && !VISIBILITY_PUBLIC.equals(upper) && !VISIBILITY_ORG.equals(upper)) {
            throw new BusinessException(400,
                    "无效的可见范围: " + visibility + "，可选值: PRIVATE / PUBLIC / ORG");
        }
        if (VISIBILITY_ORG.equals(upper) && orgId == null) {
            throw new BusinessException(400, "可见范围为 ORG 时，组织 ID 不能为空");
        }
    }

    @Override
    public void checkKbUploadPermission(KnowledgeBase kb) {
        checkKbViewPermission(kb, "向该知识库上传文档", "无权向该知识库上传文档");
    }

    @Override
    public void checkKbViewPermission(KnowledgeBase kb) {
        checkKbViewPermission(kb, "查看该知识库", "无权查看该知识库");
    }

    private void checkKbViewPermission(KnowledgeBase kb, String action, String message) {
        if (SecurityUtils.isAdmin()) return;
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long currentOrgId = SecurityUtils.getCurrentUserOrgId();

        if (kb.getOwnerId().equals(currentUserId)) return;
        if (VISIBILITY_PUBLIC.equals(kb.getVisibility())) return;
        if (VISIBILITY_ORG.equals(kb.getVisibility())
                && kb.getOrgId() != null && kb.getOrgId().equals(currentOrgId)) return;

        log.warn("越权访问被拒绝: userId={}, action={}, kbId={}", currentUserId, action, kb.getId());
        throw new BusinessException(403, message);
    }

    @Override
    public void checkKbOwnerOrAdmin(KnowledgeBase kb, String action) {
        if (SecurityUtils.isAdmin()) return;
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!kb.getOwnerId().equals(currentUserId)) {
            throw new BusinessException(403,
                    "无权" + action + "该知识库，仅知识库创建者或管理员可操作");
        }
    }

    @Override
    public void checkDocViewPermission(Document doc) {
        if (SecurityUtils.isAdmin()) return;
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long currentOrgId = SecurityUtils.getCurrentUserOrgId();

        if (doc.getOwnerId().equals(currentUserId)) return;
        if (VISIBILITY_PUBLIC.equals(doc.getVisibility())) return;
        if (VISIBILITY_ORG.equals(doc.getVisibility())
                && doc.getOrgId() != null && doc.getOrgId().equals(currentOrgId)) return;

        log.warn("越权访问被拒绝: userId={}, action=查看文档, docId={}", currentUserId, doc.getId());
        throw new BusinessException(403, "无权查看该文档");
    }

    @Override
    public void checkDocWritePermission(Document doc) {
        if (SecurityUtils.isAdmin()) return;
        Long currentUserId = SecurityUtils.getCurrentUserId();

        // owner 隐式拥有写权限
        if (doc.getOwnerId().equals(currentUserId)) return;

        // document_permission 表中显式授予 WRITE / ADMIN
        List<DocumentPermission> grants = documentPermissionMapper.selectList(
                new LambdaQueryWrapper<DocumentPermission>()
                        .eq(DocumentPermission::getDocumentId, doc.getId())
                        .eq(DocumentPermission::getUserId, currentUserId)
                        .in(DocumentPermission::getPermission, PERMISSION_WRITE, PERMISSION_ADMIN)
                        .last("LIMIT 1")
        );
        if (!grants.isEmpty()) return;

        log.warn("越权写操作被拒绝: userId={}, docId={}", currentUserId, doc.getId());
        throw new BusinessException(403, "无权编辑该文档，仅文档所有者或被授权者可编辑");
    }

    @Override
    public void checkOwnerOrAdmin(Document doc, String action) {
        if (SecurityUtils.isAdmin()) return;
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!doc.getOwnerId().equals(currentUserId)) {
            throw new BusinessException(403,
                    "无权" + action + "该文档，仅文档上传者或管理员可操作");
        }
    }
}
