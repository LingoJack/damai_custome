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
		//获取锁的名字解析处理器
		LockInfoHandle lockInfoHandle = lockInfoHandleFactory.getLockInfoHandle(LockInfoType.SERVICE_LOCK);
		//拼接锁的名字 LOCK:${name}:${key}
		String lockName = lockInfoHandle.getLockName(joinPoint, servicelock.name(), servicelock.keys());
		//锁的类型，默认 可重入锁
		LockType lockType = servicelock.lockType();
		//尝试加锁失败最多等待时间，默认10s
		long waitTime = servicelock.waitTime();
		//时间单位，默认秒
		TimeUnit timeUnit = servicelock.timeUnit();
		//获得具体的锁类型
		ServiceLocker lock = serviceLockFactory.getLock(lockType);
		//进行加锁
		boolean result = lock.tryLock(lockName, timeUnit, waitTime);
		//如果加锁成功
		if (result) {
			try {
				//执行业务逻辑
				return joinPoint.proceed();
			}
			finally {
				//解锁
				lock.unlock(lockName);
			}
		}
		else {
			log.warn("Timeout while acquiring serviceLock:{}", lockName);
			//加锁失败,如果设置了自定义处理，则执行
			String customLockTimeoutStrategy = servicelock.customLockTimeoutStrategy();
			if (StringUtil.isNotEmpty(customLockTimeoutStrategy)) {
				return handleCustomLockTimeoutStrategy(customLockTimeoutStrategy, joinPoint);
			}
			else {
				//默认处理
				servicelock.lockTimeoutStrategy().handler(lockName);
			}
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
