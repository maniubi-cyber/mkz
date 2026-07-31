package com.tianji.promotion.utils;

import com.tianji.common.exceptions.BizIllegalException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 幂等性校验切面
 * 
 * 基于Redisson + AOP实现零侵入式防重控制：
 * 1. 通过Redisson分布式锁确保同一操作不会被重复执行
 * 2. 通过Redis幂等标记防止重复请求
 * 3. 配合数据库乐观锁作为最终兜底方案
 * 
 * 双重保障优惠券发放的准确性与一致性
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IdempotentAspect {

    private final RedissonClient redissonClient;
    private final MyLockFactory myLockFactory;
    private final StringRedisTemplate redisTemplate;

    /**
     * SPEL正则
     */
    private static final Pattern SPEL_PATTERN = Pattern.compile("\\#\\{([^\\}]*)\\}");

    /**
     * 方法参数解析器
     */
    private static final ParameterNameDiscoverer PARAMETER_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
        // 1. 解析锁名称
        String lockName = parseLockName(idempotent.lockName(), pjp);
        String idempotentKey = idempotent.idempotentPrefix() + lockName;

        // 2. 检查幂等标记
        String idempotentMark = redisTemplate.opsForValue().get(idempotentKey);
        if (idempotentMark != null) {
            log.warn("重复请求，直接返回，lockName: {}", lockName);
            throw new BizIllegalException("操作已处理，请勿重复提交");
        }

        // 3. 获取分布式锁
        RLock lock = myLockFactory.getLock(idempotent.lockType(), "lock:" + lockName);
        boolean isLock = false;

        try {
            isLock = idempotent.lockStrategy().tryLock(lock, idempotent);

            if (!isLock) {
                log.warn("获取锁失败，lockName: {}", lockName);
                return null;
            }

            // 4. 双重检查幂等标记（获取锁后再次检查）
            idempotentMark = redisTemplate.opsForValue().get(idempotentKey);
            if (idempotentMark != null) {
                log.warn("重复请求（二次检查），lockName: {}", lockName);
                throw new BizIllegalException("操作已处理，请勿重复提交");
            }

            // 5. 执行业务逻辑
            Object result = pjp.proceed();

            // 6. 设置幂等标记
            redisTemplate.opsForValue().set(idempotentKey, "1", 
                    idempotent.idempotentExpire(), java.util.concurrent.TimeUnit.SECONDS);

            return result;

        } catch (BizIllegalException e) {
            throw e;
        } catch (Exception e) {
            log.error("幂等性校验异常，lockName: {}", lockName, e);
            throw e;
        } finally {
            // 7. 释放锁
            if (isLock && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 解析SpEL表达式生成锁名称
     */
    private String parseLockName(String name, ProceedingJoinPoint pjp) {
        if (!StringUtils.hasText(name) || !name.contains("#")) {
            return name;
        }

        // 构建上下文
        EvaluationContext context = new MethodBasedEvaluationContext(
                TypedValue.NULL, resolveMethod(pjp), pjp.getArgs(), PARAMETER_NAME_DISCOVERER);

        // 解析SpEL
        ExpressionParser parser = new SpelExpressionParser();
        Matcher matcher = SPEL_PATTERN.matcher(name);

        while (matcher.find()) {
            String tmp = matcher.group();
            String group = matcher.group(1);
            Expression expression = parser.parseExpression(group.charAt(0) == 'T' ? group : "#" + group);
            Object value = expression.getValue(context);
            name = name.replace(tmp, ObjectUtils.nullSafeToString(value));
        }

        return name;
    }

    /**
     * 解析方法
     */
    private Method resolveMethod(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Class<?> clazz = pjp.getTarget().getClass();
        String name = signature.getName();
        Class<?>[] parameterTypes = signature.getMethod().getParameterTypes();
        return findDeclaredMethod(clazz, name, parameterTypes);
    }

    /**
     * 查找声明的方法
     */
    private Method findDeclaredMethod(Class<?> clazz, String name, Class<?>... parameterTypes) {
        try {
            return clazz.getDeclaredMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            Class<?> superClass = clazz.getSuperclass();
            if (superClass != null) {
                return findDeclaredMethod(superClass, name, parameterTypes);
            }
        }
        return null;
    }
}
