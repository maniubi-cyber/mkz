package com.example.rag.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置类
 *
 * <p>配置功能：</p>
 * <ul>
 *   <li>分页插件 - PaginationInnerInterceptor</li>
 *   <li>乐观锁插件 - OptimisticLockerInnerInterceptor
 *       （配合 @Version 注解实现并发控制）</li>
 * </ul>
 *
 * <h3>乐观锁使用方式：</h3>
 * <pre>
 *   &#64;Version
 *   private Integer version;
 *
 *   // 更新时自动携带 version 条件
 *   // UPDATE document SET ..., version = version + 1 WHERE id = ? AND version = ?
 *   // 如果受影响行数为0，抛出 OptimisticLockerException
 * </pre>
 *
 * @author knowledge-rag-team
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * MyBatis-Plus 拦截器
     * 1. 乐观锁拦截器 - 自动处理 @Version 注解
     * 2. 分页拦截器 - 自动处理分页逻辑
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 乐观锁插件
        // 当实体类字段标注 @Version 注解时，自动在 UPDATE 语句中
        // 添加 WHERE version = ? 条件，并将 version 自增
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        // 分页插件（MySQL 数据库）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));

        return interceptor;
    }
}
