package com.mkz.promotion.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.mkz.common.autoconfigure.mybatis.MyBatisAutoFillInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MybatisPlus 拦截器配置
 *
 * 注册乐观锁拦截器（优惠券并发控制）+ 分页拦截器 + 自动填充拦截器。
 * 注：tj-common 中的 MybatisConfig 使用 @ConditionalOnMissingBean，
 * 当前模块自定义 Bean 后会覆盖通用配置，故需保留分页与自动填充能力，
 * 仅在此基础之上新增乐观锁拦截器。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 乐观锁拦截器（需在分页拦截器之前注册）
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        // 分页拦截器
        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        paginationInnerInterceptor.setMaxLimit(200L);
        interceptor.addInnerInterceptor(paginationInnerInterceptor);
        // 自动填充拦截器（保持 creater/updater 自动注入）
        interceptor.addInnerInterceptor(new MyBatisAutoFillInterceptor());
        return interceptor;
    }
}