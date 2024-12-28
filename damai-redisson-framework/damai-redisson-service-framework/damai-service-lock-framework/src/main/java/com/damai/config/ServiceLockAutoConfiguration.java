package com.damai.config;

import com.damai.constant.LockInfoType;
import com.damai.core.ManageLocker;
import com.damai.lockinfo.LockInfoHandle;
import com.damai.lockinfo.factory.LockInfoHandleFactory;
import com.damai.lockinfo.impl.ServiceLockInfoHandle;
import com.damai.servicelock.aspect.ServiceLockAspect;
import com.damai.servicelock.factory.ServiceLockFactory;
import com.damai.util.ServiceLockTool;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;

/**
 * 分布式锁 配置
 **/
public class ServiceLockAutoConfiguration {

	/**
	 * 分布式锁的key解析处理器
	 */
	@Bean(LockInfoType.SERVICE_LOCK)
	public LockInfoHandle serviceLockInfoHandle() {
		return new ServiceLockInfoHandle();
	}

	/**
	 * 锁管理
	 */
	@Bean
	public ManageLocker manageLocker(RedissonClient redissonClient) {
		return new ManageLocker(redissonClient);
	}

	/**
	 * 锁工厂
	 */
	@Bean
	public ServiceLockFactory serviceLockFactory(ManageLocker manageLocker) {
		return new ServiceLockFactory(manageLocker);
	}

	/**
	 * 分布式锁切面
	 */
	@Bean
	public ServiceLockAspect serviceLockAspect(LockInfoHandleFactory lockInfoHandleFactory, ServiceLockFactory serviceLockFactory) {
		return new ServiceLockAspect(lockInfoHandleFactory, serviceLockFactory);
	}

	/**
	 * 分布式锁工具
	 */
	@Bean
	public ServiceLockTool serviceLockUtil(LockInfoHandleFactory lockInfoHandleFactory, ServiceLockFactory serviceLockFactory) {
		return new ServiceLockTool(lockInfoHandleFactory, serviceLockFactory);
	}
}