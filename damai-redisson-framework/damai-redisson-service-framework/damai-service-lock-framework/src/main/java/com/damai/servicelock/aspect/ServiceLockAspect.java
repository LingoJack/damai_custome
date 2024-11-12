package com.damai.servicelock.aspect;

import com.damai.constant.LockInfoType;
import com.damai.util.StringUtil;
import com.damai.lockinfo.LockInfoHandle;
import com.damai.lockinfo.factory.LockInfoHandleFactory;
import com.damai.servicelock.LockType;
import com.damai.servicelock.ServiceLocker;
import com.damai.servicelock.annotion.ServiceLock;
import com.damai.servicelock.factory.ServiceLockFactory;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁 切面
 **/
@Slf4j
@Aspect
@Order(-10)
@AllArgsConstructor
public class ServiceLockAspect {

    private final LockInfoHandleFactory lockInfoHandleFactory;

    private final ServiceLockFactory serviceLockFactory;

    /**
     * 环绕通知，处理服务锁
     *
     * @param joinPoint   切入点
     * @param servicelock 服务锁注解
     * @return 执行结果
     * @throws Throwable 可能抛出的异常
     */
    @Around("@annotation(servicelock)")
    public Object around(ProceedingJoinPoint joinPoint, ServiceLock servicelock) throws Throwable {
        // 获取锁信息处理器
        LockInfoHandle lockInfoHandle = lockInfoHandleFactory.getLockInfoHandle(LockInfoType.SERVICE_LOCK);
        // 构建锁名称
        String lockName = lockInfoHandle.getLockName(joinPoint, servicelock.name(), servicelock.keys());
        // 获取锁类型
        LockType lockType = servicelock.lockType();
        // 获取等待时间
        long waitTime = servicelock.waitTime();
        // 获取时间单位
        TimeUnit timeUnit = servicelock.timeUnit();

        // 根据锁类型获取锁实例
        ServiceLocker lock = serviceLockFactory.getLock(lockType);
        // 尝试获取锁
        boolean result = lock.tryLock(lockName, timeUnit, waitTime);

        if (result) {
            try {
                // 如果获取锁成功，执行目标方法
                return joinPoint.proceed();
            }
            finally {
                // 释放锁
                lock.unlock(lockName);
            }
        }
        else {
            // 如果获取锁失败，记录日志
            log.warn("Timeout while acquiring serviceLock:{}", lockName);
            // 获取自定义锁超时策略
            String customLockTimeoutStrategy = servicelock.customLockTimeoutStrategy();
            if (StringUtil.isNotEmpty(customLockTimeoutStrategy)) {
                // 如果有自定义锁超时策略，则执行自定义策略
                return handleCustomLockTimeoutStrategy(customLockTimeoutStrategy, joinPoint);
            }
            else {
                // 如果没有自定义锁超时策略，则执行默认策略
                servicelock.lockTimeoutStrategy().handler(lockName);
            }
            // 继续执行目标方法
            return joinPoint.proceed();
        }
    }

    /**
     * 处理自定义锁超时策略
     *
     * @param customLockTimeoutStrategy 自定义锁超时策略名称
     * @param joinPoint                 切入点
     * @return 执行结果
     */
    public Object handleCustomLockTimeoutStrategy(String customLockTimeoutStrategy, JoinPoint joinPoint) {
        // 准备调用上下文
        Method currentMethod = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object target = joinPoint.getTarget();
        Method handleMethod = null;
        try {
            // 获取自定义锁超时处理方法
            handleMethod = target.getClass().getDeclaredMethod(customLockTimeoutStrategy, currentMethod.getParameterTypes());
            handleMethod.setAccessible(true);
        }
        catch (NoSuchMethodException e) {
            // 如果找不到方法，抛出异常
            throw new RuntimeException("Illegal annotation param customLockTimeoutStrategy :" + customLockTimeoutStrategy, e);
        }
        Object[] args = joinPoint.getArgs();

        // 调用自定义锁超时处理方法
        Object result;
        try {
            result = handleMethod.invoke(target, args);
        }
        catch (IllegalAccessException e) {
            // 如果访问方法失败，抛出异常
            throw new RuntimeException("Fail to illegal access custom lock timeout handler: " + customLockTimeoutStrategy, e);
        }
        catch (InvocationTargetException e) {
            // 如果调用方法失败，抛出异常
            throw new RuntimeException("Fail to invoke custom lock timeout handler: " + customLockTimeoutStrategy, e);
        }
        return result;
    }
}
