package com.example.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.rag.common.BusinessException;
import com.example.rag.entity.Document;
import com.example.rag.entity.DocumentVersionHistory;
import com.example.rag.mapper.DocumentChunkMapper;
import com.example.rag.mapper.DocumentMapper;
import com.example.rag.mapper.DocumentVersionHistoryMapper;
import com.example.rag.mapper.KnowledgeBaseMapper;
import com.example.rag.common.SecurityUtils;
import com.example.rag.service.DocumentExportService;
import com.example.rag.service.DocumentParseService;
import com.example.rag.service.MinioService;
import com.example.rag.service.PermissionService;
import com.example.rag.common.FileUploadValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档协同编辑落库 —— 服务层集成测试
 *
 * <p>覆盖 {@link DocumentServiceImpl#updateContent} 的真实行为：
 * <ul>
 *   <li>权限：可查看即可编辑，否则抛出 BusinessException</li>
 *   <li>正文通过 UpdateWrapper 持久化（last-write-wins，绕过 @Version 乐观锁）</li>
 *   <li>落一条版本历史记录</li>
 *   <li>失效文档缓存</li>
 *   <li>并发保存两者都成功（不会因 version 不匹配互抛 OptimisticLockerException）</li>
 * </ul>
 *
 * <p>用 Mockito 隔离 Mapper / Redis，专注验证保存契约本身；需 {@code mvn test} 执行。</p>
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceImplUpdateContentTest {

    private DocumentServiceImpl buildService(
            DocumentMapper docMapper,
            DocumentVersionHistoryMapper vhMapper,
            StringRedisTemplate redis,
            PermissionService permissionService) {
        DocumentChunkMapper chunkMapper = mock(DocumentChunkMapper.class);
        KnowledgeBaseMapper kbMapper = mock(KnowledgeBaseMapper.class);
        MinioService minio = mock(MinioService.class);
        FileUploadValidator validator = mock(FileUploadValidator.class);
        DocumentParseService parse = mock(DocumentParseService.class);
        DocumentExportService export = mock(DocumentExportService.class);
        return new DocumentServiceImpl(
                docMapper, chunkMapper, kbMapper, vhMapper,
                minio, validator, parse, export, permissionService, redis);
    }

    private Document privateDoc(Long ownerId, Integer version) {
        Document doc = new Document();
        doc.setId(1L);
        doc.setKbId(10L);
        doc.setOwnerId(ownerId);
        doc.setVisibility("PRIVATE");
        doc.setVersion(version);
        doc.setTitle("测试文档");
        return doc;
    }

    @Test
    @DisplayName("保存：可查看者能把正文持久化，并落版本历史、失效缓存")
    void save_persistsContentAndHistoryAndEvictsCache() {
        DocumentMapper docMapper = mock(DocumentMapper.class);
        DocumentVersionHistoryMapper vhMapper = mock(DocumentVersionHistoryMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        DocumentServiceImpl service = buildService(docMapper, vhMapper, redis, mock(PermissionService.class));

        // 当前用户是文档 owner（owner 隐式具备 WRITE 权限）
        Document doc = privateDoc(1L, 5);
        when(docMapper.selectById(1L)).thenReturn(doc);
        when(docMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(1L);

            service.updateContent(1L, "协同编辑后的正文", 3);
        }

        // 1) 正文通过 UpdateWrapper 写入（last-write-wins，不带 version 触发乐观锁）
        ArgumentCaptor<UpdateWrapper<Document>> cap = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(docMapper).update(isNull(), cap.capture());

        // 2) 版本历史落库，且记录的是本次保存的正文与编辑者
        ArgumentCaptor<DocumentVersionHistory> vhCap =
                ArgumentCaptor.forClass(DocumentVersionHistory.class);
        verify(vhMapper).insert(vhCap.capture());
        DocumentVersionHistory saved = vhCap.getValue();
        assertEquals("协同编辑后的正文", saved.getContent());
        assertEquals(1L, saved.getEditorId());
        assertEquals(Integer.valueOf(5), saved.getVersion());
        assertEquals("协同编辑保存", saved.getEditSummary());

        // 3) 失效文档缓存
        verify(redis).delete("doc:1");
    }

    @Test
    @DisplayName("权限：无 WRITE 权限者保存应被拒绝")
    void save_deniesWhenNotViewable() {
        DocumentMapper docMapper = mock(DocumentMapper.class);
        DocumentVersionHistoryMapper vhMapper = mock(DocumentVersionHistoryMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);

        // 权限服务按真实规则拒绝：非 admin、非 owner、document_permission 无 WRITE/ADMIN → 不可编辑
        PermissionService permissionService = mock(PermissionService.class);
        doThrow(new BusinessException(403, "无权编辑该文档，仅文档所有者或被授权者可编辑"))
                .when(permissionService).checkDocWritePermission(any());
        DocumentServiceImpl service = buildService(docMapper, vhMapper, redis, permissionService);

        // 文档 owner=1，当前用户=2，非 admin、未在 document_permission 中被授予写权限
        Document doc = privateDoc(1L, 1);
        when(docMapper.selectById(1L)).thenReturn(doc);

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(2L);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.updateContent(1L, "hack", 0));

            assertTrue(ex.getMessage().contains("无权"), "应提示无权限，实际: " + ex.getMessage());
        }

        // 不应有任何写入
        verify(docMapper, times(0)).update(any(), any());
        verify(vhMapper, times(0)).insert(any());
    }

    @Test
    @DisplayName("并发保存：两个用户先后保存都成功（last-write-wins，不互抛乐观锁异常）")
    void concurrentSaves_bothSucceedLastWriteWins() {
        DocumentMapper docMapper = mock(DocumentMapper.class);
        DocumentVersionHistoryMapper vhMapper = mock(DocumentVersionHistoryMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        DocumentServiceImpl service = buildService(docMapper, vhMapper, redis, mock(PermissionService.class));

        // 同一文档被两人（owner=1 与另一可查看用户=2）先后保存；selectById 始终返回同一份（version 不变）
        Document doc = privateDoc(1L, 5);
        doc.setVisibility("PUBLIC");
        when(docMapper.selectById(1L)).thenReturn(doc);
        when(docMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> sec = mockStatic(SecurityUtils.class)) {
            sec.when(SecurityUtils::isAdmin).thenReturn(false);
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(2L);

            // 两次保存都不应抛 OptimisticLockerException
            service.updateContent(1L, "用户2的版本", 3);
            sec.when(SecurityUtils::getCurrentUserId).thenReturn(1L);
            service.updateContent(1L, "用户1的版本", 3);
        }

        // 两次都触发了正文写入与版本历史（最后写入者胜出，覆盖前者）
        verify(docMapper, times(2)).update(isNull(), any(UpdateWrapper.class));
        verify(vhMapper, times(2)).insert(any());

        // 校验最后一次保存写入的确实是"用户1的版本"
        ArgumentCaptor<DocumentVersionHistory> vhCap =
                ArgumentCaptor.forClass(DocumentVersionHistory.class);
        verify(vhMapper, times(2)).insert(vhCap.capture());
        List<DocumentVersionHistory> all = vhCap.getAllValues();
        assertEquals("用户1的版本", all.get(all.size() - 1).getContent());
    }
}
