package com.damai.lockinfo.factory;

import com.damai.lockinfo.LockInfoHandle;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * 锁信息工厂
 * 该工厂类用于根据不同的锁信息类型获取对应的锁信息处理对象
 * 实现了ApplicationContextAware接口，以便能够使用Spring的依赖注入功能
 */
public class LockInfoHandleFactory implements ApplicationContextAware {

	// Spring应用上下文
	private ApplicationContext applicationContext;

	/**
	 * 根据锁信息类型获取对应的锁信息处理对象
	 *
	 * @param lockInfoType 锁信息类型，用于确定获取哪种类型的LockInfoHandle实现类
	 * @return LockInfoHandle 实例，根据给定的锁信息类型获取
	 */
	public LockInfoHandle getLockInfoHandle(String lockInfoType) {
		return applicationContext.getBean(lockInfoType, LockInfoHandle.class);
	}

	/**
	 * 设置Spring应用上下文
	 *
	 * @param applicationContext Spring应用上下文
	 * @throws BeansException 如果设置过程中出现错误
	 */
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
}
