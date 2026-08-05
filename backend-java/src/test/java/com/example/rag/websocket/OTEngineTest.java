package com.example.rag.websocket;

import com.example.rag.websocket.OTEngine.OpType;
import com.example.rag.websocket.OTEngine.Operation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OT 引擎正确性测试
 *
 * <p>协同编辑的正确性无法靠肉眼 review 保证，必须用穷举验证 TP1 收敛性：
 * 两个站点以相反顺序应用同一对并发操作后，文档内容必须完全一致。
 * 任何一组反例在线上都意味着两个用户的文档永久分叉。</p>
 */
class OTEngineTest {

    private static final String DOC = "abcdefgh";

    /**
     * 构造覆盖各种边界的操作集合：
     * 插入点覆盖首/中/尾，删除区间覆盖所有起点与长度组合，外加一个空操作。
     */
    private List<Operation> buildOperationSet() {
        List<Operation> ops = new ArrayList<>();
        for (int p = 0; p <= DOC.length(); p++) {
            ops.add(new Operation(OpType.INSERT, p, "X", 0, 0, 1L));
            ops.add(new Operation(OpType.INSERT, p, "YZ", 0, 0, 2L));
        }
        for (int p = 0; p < DOC.length(); p++) {
            for (int len = 1; len <= DOC.length() - p; len++) {
                ops.add(new Operation(OpType.DELETE, p, "", len, 0, 3L));
            }
        }
        ops.add(new Operation(OpType.RETAIN, 0, "", 0, 3, 4L));
        return ops;
    }

    @Test
    @DisplayName("TP1: 任意两个并发操作，双端以相反顺序应用后必须收敛到同一文档")
    void transformSatisfiesConvergence() {
        List<Operation> ops = buildOperationSet();
        List<String> failures = new ArrayList<>();
        int total = 0;

        for (Operation op1 : ops) {
            for (Operation op2 : ops) {
                total++;

                // site1: 先应用自己的 op1，再应用「op2 相对 op1」的变换结果
                String site1 = OTEngine.apply(
                        OTEngine.apply(DOC, op1), OTEngine.transform(op2, op1)[0]);
                // site2: 先应用自己的 op2，再应用「op1 相对 op2」的变换结果
                String site2 = OTEngine.apply(
                        OTEngine.apply(DOC, op2), OTEngine.transform(op1, op2)[0]);

                if (!site1.equals(site2)) {
                    failures.add(String.format("op1=%s op2=%s → '%s' vs '%s'",
                            op1, op2, site1, site2));
                }
            }
        }

        assertTrue(failures.isEmpty(),
                () -> String.format("%d/%d 组并发操作发生分叉，前 5 例:%n%s",
                        failures.size(), ops.size() * ops.size(),
                        String.join("\n", failures.subList(0, Math.min(5, failures.size())))));
        assertTrue(total > 3000, "测试样本量过小，覆盖不足");
    }

    @Test
    @DisplayName("同位置插入靠 siteId 做确定性排序，而非参数顺序")
    void sameePositionInsertUsesSiteIdTieBreak() {
        Operation a = new Operation(OpType.INSERT, 3, "A", 0, 0, 101L);
        Operation b = new Operation(OpType.INSERT, 3, "B", 0, 0, 202L);

        String site1 = OTEngine.apply(OTEngine.apply(DOC, a), OTEngine.transform(b, a)[0]);
        String site2 = OTEngine.apply(OTEngine.apply(DOC, b), OTEngine.transform(a, b)[0]);

        assertEquals(site1, site2, "同位置插入必须收敛");
        assertEquals("abcABdefgh", site1, "siteId 小的操作排在前面");
    }

    @Test
    @DisplayName("siteId 在多轮变换中不能丢失")
    void siteIdSurvivesRepeatedTransform() {
        OTEngine engine = new OTEngine();
        Operation client = new Operation(OpType.INSERT, 2, "Z", 0, 0, 55L);

        Operation cur = client;
        cur = engine.transformOp(cur, new Operation(OpType.INSERT, 0, "1", 0, 0, 66L));
        cur = engine.transformOp(cur, new Operation(OpType.DELETE, 4, "", 2, 0, 77L));
        cur = engine.transformOp(cur, new Operation(OpType.INSERT, 2, "9", 0, 0, 88L));

        assertEquals(55L, cur.getSiteId(), "变换后 siteId 必须保留，否则 tie-break 退化");
    }

    @Test
    @DisplayName("transformOp 不得修改传入的操作对象")
    void transformDoesNotMutateInput() {
        OTEngine engine = new OTEngine();
        Operation op1 = new Operation(OpType.INSERT, 5, "hello", 0, 0, 1L);
        Operation op2 = new Operation(OpType.DELETE, 0, "", 3, 0, 2L);

        engine.transformOp(op1, op2);

        assertEquals(5, op1.getPos(), "入参 op1 位置被意外修改");
        assertEquals("hello", op1.getText());
        assertEquals(0, op2.getPos(), "入参 op2 位置被意外修改");
        assertEquals(3, op2.getLength());
    }

    @Test
    @DisplayName("插入点落在并发删除区间内部时，双端仍收敛")
    void insertInsideConcurrentDeleteConverges() {
        String doc = "Hello World";
        Operation del = new Operation(OpType.DELETE, 6, "", 5, 0, 1L);   // 删 "World"
        Operation ins = new Operation(OpType.INSERT, 8, "XX", 0, 0, 2L); // 在 World 内部插入

        String site1 = OTEngine.apply(OTEngine.apply(doc, del), OTEngine.transform(ins, del)[0]);
        String site2 = OTEngine.apply(OTEngine.apply(doc, ins), OTEngine.transform(del, ins)[0]);

        assertEquals(site1, site2);
        assertEquals("Hello ", site1, "单区间模型下插入被删除吞掉，两端一致");
    }

    @Test
    @DisplayName("删除区间重叠时取并集，双端收敛")
    void overlappingDeletesConverge() {
        String doc = "Hello World";
        Operation d1 = new Operation(OpType.DELETE, 4, "", 4, 0, 1L);  // [4,8)
        Operation d2 = new Operation(OpType.DELETE, 6, "", 5, 0, 2L);  // [6,11)

        String site1 = OTEngine.apply(OTEngine.apply(doc, d1), OTEngine.transform(d2, d1)[0]);
        String site2 = OTEngine.apply(OTEngine.apply(doc, d2), OTEngine.transform(d1, d2)[0]);

        assertEquals(site1, site2);
        assertEquals("Hell", site1, "重叠删除应等价于删除并集 [4,11)");
    }

    @Test
    @DisplayName("越界位置必须安全降级，不能抛异常")
    void outOfRangePositionsAreClamped() {
        assertEquals("abc", OTEngine.apply("abc", new Operation(OpType.DELETE, 10, "", 5, 0)));
        assertEquals("abcX", OTEngine.apply("abc", new Operation(OpType.INSERT, 99, "X", 0, 0)));
        assertEquals("", OTEngine.apply("", new Operation(OpType.DELETE, 0, "", 3, 0)));
        assertEquals("abc", OTEngine.apply(null, new Operation(OpType.INSERT, 0, "abc", 0, 0)));
    }

    @Test
    @DisplayName("空操作参与变换时不影响对方")
    void noopDoesNotAffectCounterpart() {
        Operation noop = Operation.noop();
        Operation ins = new Operation(OpType.INSERT, 2, "K", 0, 0, 1L);

        Operation result = OTEngine.transform(ins, noop)[0];

        assertEquals(2, result.getPos());
        assertEquals("K", result.getText());
        assertEquals("abKcdefgh", OTEngine.apply(DOC, result));
    }
}
