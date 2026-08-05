package com.example.rag.event;

/**
 * 文档解析触发事件。
 *
 * <p>在 upload / reparse 等事务方法内发布，由
 * {@code DocumentParseService} 的 {@code @TransactionalEventListener}
 * 在事务提交后异步触发 Python 解析，避免异步线程读到未提交的数据。</p>
 *
 * @author knowledge-rag-team
 */
public class DocumentParseTriggerEvent {

    private final Long docId;

    public DocumentParseTriggerEvent(Long docId) {
        this.docId = docId;
    }

    public Long getDocId() {
        return docId;
    }
}
