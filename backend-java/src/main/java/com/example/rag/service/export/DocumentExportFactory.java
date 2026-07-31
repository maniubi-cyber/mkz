package com.example.rag.service.export;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档导出策略工厂 - 工厂模式
 *
 * <p>通过工厂模式管理所有导出策略，根据格式类型获取对应的导出策略。</p>
 *
 * <h3>设计模式说明：</h3>
 * <ul>
 *   <li>策略模式 (Strategy Pattern)：定义一系列导出算法（PDF/Word/MD），封装到各自的策略类中</li>
 *   <li>工厂模式 (Factory Pattern)：通过工厂类创建和管理策略实例，隐藏具体实现</li>
 * </ul>
 *
 * <h3>优势：</h3>
 * <ul>
 *   <li>消除 if-else 判断：原代码使用大量 if-else 判断格式类型</li>
 *   <li>代码复杂度显著降低：每种导出逻辑封装为独立策略类</li>
 *   <li>可拓展性增强：新增格式只需添加类 + 注册到工厂，无需修改现有代码</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>
 *   // 通过工厂获取策略
 *   DocumentExportStrategy strategy = DocumentExportFactory.getStrategy("pdf");
 *
 *   // 导出文档
 *   ByteArrayOutputStream output = strategy.export(document);
 * </pre>
 *
 * @author knowledge-rag-team
 */
@Slf4j
@Component
public class DocumentExportFactory {

    /** 策略映射表：格式类型 -> 策略实例 */
    private static final Map<String, DocumentExportStrategy> STRATEGY_MAP = new HashMap<>();

    /**
     * 构造函数 - 自动注册所有策略
     *
     * <p>Spring 会自动注入所有 DocumentExportStrategy 实现类，
     * 工厂根据每个策略的 formatType 进行注册。</p>
     */
    @Autowired
    public DocumentExportFactory(List<DocumentExportStrategy> strategies) {
        for (DocumentExportStrategy strategy : strategies) {
            STRATEGY_MAP.put(strategy.getFormatType().toLowerCase(), strategy);
            log.info("注册文档导出策略: formatType={}, class={}",
                    strategy.getFormatType(), strategy.getClass().getSimpleName());
        }
        log.info("文档导出策略工厂初始化完成，共 {} 种格式", STRATEGY_MAP.size());
    }

    /**
     * 根据格式类型获取导出策略
     *
     * @param formatType 格式类型（pdf / word / markdown）
     * @return 对应的导出策略
     * @throws IllegalArgumentException 如果格式类型不支持
     */
    public static DocumentExportStrategy getStrategy(String formatType) {
        DocumentExportStrategy strategy = STRATEGY_MAP.get(formatType.toLowerCase());
        if (strategy == null) {
            throw new IllegalArgumentException(
                    "不支持的导出格式: " + formatType +
                            "，支持的格式: " + STRATEGY_MAP.keySet());
        }
        return strategy;
    }

    /**
     * 获取所有支持的导出格式
     *
     * @return 支持的格式类型集合
     */
    public static Map<String, DocumentExportStrategy> getAllStrategies() {
        return new HashMap<>(STRATEGY_MAP);
    }
}
