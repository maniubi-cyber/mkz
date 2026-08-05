package com.example.rag.websocket;

import org.springframework.stereotype.Component;

/**
 * OT (Operational Transformation) 引擎
 *
 * <p>实现经典的 OT 算法用于协同文本编辑。</p>
 *
 * <p>支持三种原子操作:</p>
 * <ul>
 *   <li>INSERT(position, text) — 在指定位置插入文本</li>
 *   <li>DELETE(position, length) — 从指定位置删除指定长度文本</li>
 *   <li>RETAIN(count) — 保留指定长度不变（也用于表示 no-op）</li>
 * </ul>
 *
 * <p>核心不变量 (TP1, Transformation Property 1):</p>
 * <pre>
 *   apply(apply(doc, op1), transform(op2, op1)[0])
 *     == apply(apply(doc, op2), transform(op1, op2)[0])
 * </pre>
 *
 * <p>约定：{@code transform(op1, op2)} 返回长度为 2 的数组，
 * 下标 0 是「op1 相对于 op2 变换后的结果」，下标 1 是「op2 相对于 op1 变换后的结果」。</p>
 *
 * @see <a href="https://en.wikipedia.org/wiki/Operational_transformation">Operational Transformation</a>
 */
@Component
public class OTEngine {

    /** 操作类型 */
    public enum OpType {
        INSERT, DELETE, RETAIN
    }

    /**
     * 原子编辑操作
     *
     * <p>注意：字段刻意设计为可变，因为 transform 过程需要就地调整位置与长度。
     * 所有对外的 transform 入口都会先 {@link #copy(Operation)} 一份，
     * 不会破坏调用方持有的原始对象。</p>
     */
    public static class Operation {
        private OpType type;
        private int pos;
        private String text;
        private int length;
        private int count;

        /**
         * 站点标识（通常取用户 ID）。
         *
         * <p>仅用于两个 INSERT 落在<b>完全相同位置</b>时的确定性排序。
         * tie-break 必须依赖操作自身的固有属性——如果改用「函数参数顺序」来决定谁在前，
         * 两个站点调用 transform 时参数顺序天然相反，会各自认为自己优先，导致文档永久分叉。</p>
         */
        private long siteId;

        public Operation(OpType type, int pos, String text, int length, int count) {
            this(type, pos, text, length, count, 0L);
        }

        public Operation(OpType type, int pos, String text, int length, int count, long siteId) {
            this.type = type;
            this.pos = pos;
            this.text = text == null ? "" : text;
            this.length = length;
            this.count = count;
            this.siteId = siteId;
        }

        /** 构造一个空操作（RETAIN 0） */
        public static Operation noop() {
            return new Operation(OpType.RETAIN, 0, "", 0, 0);
        }

        public OpType getType() { return type; }
        public int getPos() { return pos; }
        public String getText() { return text; }
        public int getLength() { return length; }
        public int getCount() { return count; }
        public long getSiteId() { return siteId; }

        public void setType(OpType type) { this.type = type; }
        public void setPos(int pos) { this.pos = pos; }
        public void setText(String text) { this.text = text == null ? "" : text; }
        public void setLength(int length) { this.length = length; }
        public void setCount(int count) { this.count = count; }
        public void setSiteId(long siteId) { this.siteId = siteId; }

        /** 是否为空操作（不会改变文档内容） */
        public boolean isNoop() {
            if (type == OpType.RETAIN) return true;
            if (type == OpType.INSERT) return text.isEmpty();
            return length <= 0;
        }

        /** 该操作插入的字符数 */
        private int insertLen() {
            return type == OpType.INSERT ? text.length() : 0;
        }

        /** 退化为空操作 */
        private void degradeToNoop() {
            this.type = OpType.RETAIN;
            this.text = "";
            this.length = 0;
            this.count = 0;
        }

        @Override
        public String toString() {
            if (type == OpType.INSERT) return String.format("OP(I, %d, '%s')", pos, text);
            if (type == OpType.DELETE) return String.format("OP(D, %d, %d)", pos, length);
            return String.format("OP(R, %d, %d)", pos, count);
        }
    }

    /**
     * 变换单个操作：返回 op1 相对于「已应用的 op2」变换后的结果
     *
     * @param op1 待变换的操作（通常来自落后版本的客户端）
     * @param op2 服务器已应用的操作
     * @return 变换后的 op1（新对象，不修改入参）
     */
    public Operation transformOp(Operation op1, Operation op2) {
        return transform(op1, op2)[0];
    }

    /**
     * 双向变换两个并发操作
     *
     * @return {@code [op1', op2']}，均为新对象，不修改入参
     */
    public static Operation[] transform(Operation op1, Operation op2) {
        Operation o1 = copy(op1);
        Operation o2 = copy(op2);

        // 任一方为空操作时，双方都无需调整
        if (o1.isNoop() || o2.isNoop()) {
            return new Operation[]{o1, o2};
        }

        if (o1.type == OpType.INSERT && o2.type == OpType.INSERT) {
            return transformInsertInsert(o1, o2);
        } else if (o1.type == OpType.INSERT && o2.type == OpType.DELETE) {
            return transformInsertDelete(o1, o2);
        } else if (o1.type == OpType.DELETE && o2.type == OpType.INSERT) {
            // 镜像复用 INSERT vs DELETE，避免两处逻辑不一致
            Operation[] mirrored = transformInsertDelete(o2, o1);
            return new Operation[]{mirrored[1], mirrored[0]};
        } else if (o1.type == OpType.DELETE && o2.type == OpType.DELETE) {
            return transformDeleteDelete(o1, o2);
        }

        // RETAIN 参与的组合已在 isNoop 分支处理，理论上不可达
        return new Operation[]{o1, o2};
    }

    // ============================================================
    // Case: INSERT vs INSERT
    // ============================================================

    private static Operation[] transformInsertInsert(Operation o1, Operation o2) {
        if (o1.pos < o2.pos) {
            // o1 在前 → o1 不变，o2 被 o1 插入的内容右推
            o2.pos += o1.insertLen();
        } else if (o1.pos > o2.pos) {
            // o2 在前 → o2 不变，o1 被 o2 插入的内容右推
            o1.pos += o2.insertLen();
        } else {
            // 同一位置：必须用「操作固有属性」做确定性 tie-break，
            // 保证两个站点独立计算时得出一致的先后顺序。
            // 依次比较 siteId → 插入文本；两者都相同时结果本就一致，任选其一即可。
            if (o1FirstAtSamePos(o1, o2)) {
                o2.pos += o1.insertLen();
            } else {
                o1.pos += o2.insertLen();
            }
        }
        return new Operation[]{o1, o2};
    }

    /** 同位置插入时判定 o1 是否排在 o2 之前（结果与参数顺序无关） */
    private static boolean o1FirstAtSamePos(Operation o1, Operation o2) {
        if (o1.siteId != o2.siteId) {
            return o1.siteId < o2.siteId;
        }
        int cmp = o1.text.compareTo(o2.text);
        // 文本也完全相同时，两种顺序产生的文档内容一致，不影响收敛
        return cmp <= 0;
    }

    // ============================================================
    // Case: INSERT vs DELETE  (o1 = INSERT, o2 = DELETE)
    // ============================================================

    private static Operation[] transformInsertDelete(Operation o1, Operation o2) {
        final int insertPos = o1.pos;
        final int insertLen = o1.insertLen();
        final int deleteStart = o2.pos;
        final int deleteEnd = o2.pos + o2.length;

        if (insertPos <= deleteStart) {
            // 插入点在删除区间之前（含恰好等于起点）：
            //   o1 不变；o2 的删除区间整体右移
            o2.pos = deleteStart + insertLen;
        } else if (insertPos >= deleteEnd) {
            // 插入点在删除区间之后（含恰好等于终点）：
            //   o1 左移被删掉的长度；o2 不受影响
            o1.pos = insertPos - o2.length;
        } else {
            // 插入点严格落在删除区间内部。
            //
            // 理想语义是保留插入内容、把删除劈成两段，但本模型用单个 (pos, length)
            // 表示删除，无法表达两段区间。此时只有「插入被删除吞掉」这一种自洽解：
            //   o1 → 空操作
            //   o2 → 删除区间扩展 insertLen，把插入的内容一并删掉
            // 否则一侧保留插入、另一侧吞掉插入，双方文档会永久分叉。
            //
            // 权衡说明：并发编辑时，若他人删除的区间正好覆盖你的输入位置，你刚敲的字会丢失。
            // 若要保住这段输入，需要把 Operation 升级为「操作序列」模型（retain/insert/delete 组合），
            // 让删除能够拆分——那是 ot.js 的做法，会同时改动前端协议。
            o1.degradeToNoop();
            o2.length = o2.length + insertLen;
        }

        return new Operation[]{o1, o2};
    }

    // ============================================================
    // Case: DELETE vs DELETE
    // ============================================================

    private static Operation[] transformDeleteDelete(Operation o1, Operation o2) {
        // 先用不可变快照记录原始区间，避免边改边算导致相互污染
        final int start1 = o1.pos;
        final int end1 = o1.pos + o1.length;
        final int len1 = o1.length;
        final int start2 = o2.pos;
        final int end2 = o2.pos + o2.length;
        final int len2 = o2.length;

        // 重叠区间长度（无重叠时为 0）
        final int overlap = Math.max(0, Math.min(end1, end2) - Math.max(start1, start2));

        // --- o1'：扣掉已被 o2 删除的部分，再按 o2 删除量左移 ---
        int newLen1 = len1 - overlap;
        if (newLen1 <= 0) {
            o1.degradeToNoop();
        } else {
            // 新起点：o1 起点若落在 o2 删除区间内，则坍缩到 o2 起点
            int newStart1 = start1 >= start2 && start1 < end2 ? start2 : start1;
            // 左移量：o2 中位于 o1 新起点之前、且确实被删除的字符数
            newStart1 -= Math.max(0, Math.min(end2, newStart1) - start2);
            o1.pos = Math.max(0, newStart1);
            o1.length = newLen1;
        }

        // --- o2'：对称处理 ---
        int newLen2 = len2 - overlap;
        if (newLen2 <= 0) {
            o2.degradeToNoop();
        } else {
            int newStart2 = start2 >= start1 && start2 < end1 ? start1 : start2;
            newStart2 -= Math.max(0, Math.min(end1, newStart2) - start1);
            o2.pos = Math.max(0, newStart2);
            o2.length = newLen2;
        }

        return new Operation[]{o1, o2};
    }

    // ============================================================
    // Utility
    // ============================================================

    private static Operation copy(Operation op) {
        // siteId 必须一并复制：它是 tie-break 依据，
        // 若在变换过程中丢失，后续同位置插入会退化成不确定排序。
        return new Operation(op.type, op.pos, op.text, op.length, op.count, op.siteId);
    }

    /**
     * 将操作应用到文档文本
     *
     * @param text 原文档内容
     * @param op   要应用的操作
     * @return 应用后的新文档内容
     */
    public static String apply(String text, Operation op) {
        if (text == null) text = "";
        if (op == null || op.isNoop()) return text;

        if (op.type == OpType.INSERT) {
            int pos = clamp(op.pos, 0, text.length());
            return text.substring(0, pos) + op.text + text.substring(pos);
        } else if (op.type == OpType.DELETE) {
            int pos = clamp(op.pos, 0, text.length());
            int end = clamp(pos + op.length, pos, text.length());
            return text.substring(0, pos) + text.substring(end);
        }
        // RETAIN is a no-op
        return text;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
