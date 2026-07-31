package com.example.rag.websocket;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * WebSocket 消息协议 - 实时协作编辑
 *
 * <p>定义客户端与服务器之间的消息格式，支持 OT 算法的并发编辑操作。</p>
 *
 * @author knowledge-rag-team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebSocketMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息类型
     * - operation: 编辑操作（插入/删除/保留）
     * - cursor: 光标位置更新
     * - sync: 文档同步（请求/响应）
     * - join: 用户加入编辑
     * - leave: 用户离开编辑
     * - error: 错误消息
     */
    private String type;

    /** 文档 ID */
    private Long documentId;

    /** 当前文档版本号 */
    private Integer version;

    /** 操作用户 ID */
    private Long userId;

    /** 操作用户名 */
    private String username;

    /** 编辑操作详情 */
    private Operation operation;

    /** 光标位置 */
    private CursorPosition cursor;

    /** 时间戳 */
    private Long timestamp;

    /** 错误信息 */
    private String error;

    /**
     * 编辑操作 - OT 算法基础操作
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Operation implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 操作类型
         * - insert: 插入文本
         * - delete: 删除文本
         * - retain: 保留（跳过）指定长度
         */
        private String type;

        /** 操作起始位置（文档中的字符偏移量） */
        private Integer position;

        /** 插入的内容（insert 时使用） */
        private String content;

        /** 删除长度（delete 时使用） */
        private Integer length;

        /** 保留长度（retain 时使用） */
        private Integer retain;
    }

    /**
     * 光标位置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CursorPosition implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 光标起始位置 */
        private Integer start;

        /** 光标结束位置（选中区域） */
        private Integer end;

        /** 用户颜色（区分不同用户） */
        private String color;
    }
}
