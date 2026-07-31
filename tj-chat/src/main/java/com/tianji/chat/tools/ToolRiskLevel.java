package com.tianji.chat.tools;

/**
 * 工具风险等级枚举
 * 
 * 工具按风险程度分为三级：
 * - LOW（低级）：课程查询、排行榜查看等只读操作，直接执行
 * - MEDIUM（中级）：优惠券领取、点赞等写操作，执行前校验幂等
 * - HIGH（高级）：课程上下架、违规评论处理等敏感操作，触发Human-in-the-Loop审批
 */
public enum ToolRiskLevel {
    /**
     * 低级风险：只读操作，直接执行
     * 例如：课程查询、排行榜查看
     */
    LOW(1, "低级", "只读操作，可直接执行"),

    /**
     * 中级风险：写操作，执行前校验幂等
     * 例如：优惠券领取、点赞
     */
    MEDIUM(2, "中级", "写操作，执行前校验幂等"),

    /**
     * 高级风险：敏感操作，触发Human-in-the-Loop审批
     * 例如：课程上下架、违规评论处理
     */
    HIGH(3, "高级", "敏感操作，需人工审批");

    private final int level;
    private final String name;
    private final String description;

    ToolRiskLevel(int level, String name, String description) {
        this.level = level;
        this.name = name;
        this.description = description;
    }

    public int getLevel() {
        return level;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
