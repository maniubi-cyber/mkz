package com.example.rag.websocket;

/**
 * OT (Operational Transformation) 引擎
 *
 * <p>实现经典的 OT 算法用于协同文本编辑。</p>
 *
 * <p>支持三种原子操作:</p>
 * <ul>
 *   <li>INSERT(position, text) — 在指定位置插入文本</li>
 *   <li>DELETE(position, length) — 从指定位置删除指定长度文本</li>
 *   <li>RETAIN(count) — 保留指定长度不变</li>
 * </ul>
 *
 * <p>核心不变量:</p>
 * <pre>
 *   apply(apply(doc, op1), op2) == apply(apply(doc, op2), transform(op1, op2))
 * </pre>
 *
 * @see <a href="https://trimitra.com/ot/">A Theory of Collaborative Editing</a> — Ellis & Gibbs, 1989
 */
public class OTEngine {

    /** 操作类型 */
    public enum OpType {
        INSERT, DELETE, RETAIN
    }

    /**
     * 原子编辑操作
     */
    public static class Operation {
        private final OpType type;
        private final int pos;
        private final String text;
        private final int length;
        private final int count;

        public Operation(OpType type, int pos, String text, int length, int count) {
            this.type = type;
            this.pos = pos;
            this.text = text;
            this.length = length;
            this.count = count;
        }

        public OpType getType() { return type; }
        public int getPos() { return pos; }
        public String getText() { return text; }
        public int getLength() { return length; }
        public int getCount() { return count; }

        @Override
        public String toString() {
            if (type == OpType.INSERT) return String.format("OP(I, %d, '%s')", pos, text);
            if (type == OpType.DELETE) return String.format("OP(D, %d, %d)", pos, length);
            return String.format("OP(R, %d, %d)", pos, count);
        }
    }

    /**
     * 变换两个并发操作
     *
     * @param op1    第一个操作（客户端 A 发送）
     * @param op2    第二个操作（服务器已应用）
     * @param prefer 冲突时优先策略（"op1" 表示 op1 优先）
     * @return 变换后的 op1
     */
    public Operation transformOp(Operation op1, Operation op2) {
        Operation o1 = copy(op1);
        Operation o2 = copy(op2);
        return transform(o1, o2).getX();
    }

    /**
     * 双向变换两个并发操作
     *
     * @return (op1_transformed, op2_transformed)
     */
    public static Operation[] transform(Operation op1, Operation op2) {
        Operation o1 = copy(op1);
        Operation o2 = copy(op2);

        if (o1.type == OpType.INSERT && o2.type == OpType.INSERT) {
            return transformInsertInsert(o1, o2);
        } else if (o1.type == OpType.INSERT && o2.type == OpType.DELETE) {
            return transformInsertDelete(o1, o2);
        } else if (o1.type == OpType.DELETE && o2.type == OpType.INSERT) {
            return transformDeleteInsert(o1, o2);
        } else if (o1.type == OpType.DELETE && o2.type == OpType.DELETE) {
            return transformDeleteDelete(o1, o2);
        } else if (o1.type == OpType.RETAIN || o2.type == OpType.RETAIN) {
            return new Operation[]{o1, o2};
        }

        throw new IllegalArgumentException(
                "Unsupported operation types: " + o1.type + ", " + o2.type);
    }

    // ============================================================
    // Case: INSERT vs INSERT
    // ============================================================

    private static Operation[] transformInsertInsert(Operation o1, Operation o2) {
        if (o1.pos < o2.pos) {
            // o1 在 o2 之前 → o1 不变，o2 右移
        } else if (o1.pos > o2.pos) {
            o2.pos += o1.text.length();
        } else {
            // 同一位置 → o2 排在后面
            o2.pos += o1.text.length();
        }
        return new Operation[]{o1, o2};
    }

    // ============================================================
    // Case: INSERT vs DELETE
    // ============================================================

    private static Operation[] transformInsertDelete(Operation o1, Operation o2) {
        // o1: INSERT, o2: DELETE
        int deleteEnd = o2.pos + o2.length;

        if (o1.pos < o2.pos) {
            // 插入在删除范围之前 → 不变
        } else if (o1.pos >= deleteEnd) {
            // 插入在删除范围之后 → 左移
            o1.pos -= o2.length;
        } else {
            // 插入在删除范围内 → 移到删除末尾
            o1.pos = deleteEnd;
        }
        return new Operation[]{o1, o2};
    }

    // ============================================================
    // Case: DELETE vs INSERT
    // ============================================================

    private static Operation[] transformDeleteInsert(Operation o1, Operation o2) {
        // o1: DELETE, o2: INSERT
        int deleteEnd = o1.pos + o1.length;

        if (o2.pos < o1.pos) {
            // 插入在删除范围之前 → 不变
        } else if (o2.pos >= deleteEnd) {
            // 插入在删除范围之后 → 左移
            o2.pos -= o1.length;
        } else {
            // 插入在删除范围内 → 移到删除末尾
            o2.pos = deleteEnd;
        }
        return new Operation[]{o1, o2};
    }

    // ============================================================
    // Case: DELETE vs DELETE
    // ============================================================

    private static Operation[] transformDeleteDelete(Operation o1, Operation o2) {
        int delete1Start = o1.pos;
        int delete1End = o1.pos + o1.length;
        int delete2Start = o2.pos;
        int delete2End = o2.pos + o2.length;

        // 不相交
        if (delete1End <= delete2Start || delete2End <= delete1Start) {
            if (o2.pos >= delete1End) o2.pos -= o1.length;
            if (o1.pos >= delete2End) o1.pos -= o2.length;
            return new Operation[]{o1, o2};
        }

        // 有交集，计算交集
        int intersectStart = Math.max(delete1Start, delete2Start);
        int intersectEnd = Math.min(delete1End, delete2End);
        int intersectLen = intersectEnd - intersectStart;

        // o1 保留交集前的部分
        if (delete1Start < intersectStart) {
            o1.length = intersectStart - delete1Start;
        } else {
            o1.length -= intersectLen;
            if (o1.length <= 0) {
                o1.type = OpType.RETAIN;
                o1.count = 0;
            }
        }

        // o2 保留交集前的部分
        if (delete2Start < intersectStart) {
            o2.length = intersectStart - delete2Start;
        } else {
            o2.length -= intersectLen;
            if (o2.length <= 0) {
                o2.type = OpType.RETAIN;
                o2.count = 0;
            }
        }

        // 调整位置
        if (o2.pos >= delete1End) o2.pos -= o1.length;
        if (o1.pos >= delete2End) o1.pos -= o2.length;

        return new Operation[]{o1, o2};
    }

    // ============================================================
    // Utility
    // ============================================================

    private static Operation copy(Operation op) {
        return new Operation(op.type, op.pos, op.text, op.length, op.count);
    }

    /**
     * 将操作应用到文档文本
     */
    public static String apply(String text, Operation op) {
        if (op.type == OpType.INSERT) {
            int pos = Math.min(op.pos, text.length());
            return text.substring(0, pos) + op.text + text.substring(pos);
        } else if (op.type == OpType.DELETE) {
            int pos = Math.min(op.pos, text.length());
            int end = Math.min(pos + op.length, text.length());
            return text.substring(0, pos) + text.substring(end);
        }
        // RETAIN is a no-op
        return text;
    }
}
