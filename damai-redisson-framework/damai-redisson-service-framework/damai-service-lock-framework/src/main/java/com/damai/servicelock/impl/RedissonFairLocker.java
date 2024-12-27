package com.damai.servicelock.impl;

import com.damai.servicelock.ServiceLocker;
import lombok.AllArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁 公平锁
 * <p>
 * 该类实现了ServiceLocker接口，使用RedissonClient提供的公平锁机制来管理分布式锁
 * 公平锁确保锁的获取顺序与请求顺序一致，适用于需要保证操作顺序的场景
 **/
@AllArgsConstructor
public class RedissonFairLocker implements ServiceLocker {

	// Redisson客户端，用于连接Redis服务器并操作分布式锁
	private final RedissonClient redissonClient;

	/**
	 * 获取一个公平锁对象，但不锁定
	 *
	 * @param lockKey 锁的唯一键，用于标识锁
	 * @return 返回一个RLock对象
	 */
	@Override
	public RLock getLock(String lockKey) {
		return redissonClient.getFairLock(lockKey);
	}

	/**
	 * 获取并锁定一个公平锁对象
	 *
	 * @param lockKey 锁的唯一键，用于标识锁
	 * @return 返回一个已锁定的RLock对象
	 */
	@Override
	public RLock lock(String lockKey) {
		RLock lock = redissonClient.getFairLock(lockKey);
		lock.lock();
		return lock;
	}

	/**
	 * 获取并锁定一个公平锁对象，带有租约时间
	 *
	 * @param lockKey   锁的唯一键，用于标识锁
	 * @param leaseTime 锁的租约时间（秒）
	 * @return 返回一个带有租约时间的已锁定RLock对象
	 */
	@Override
	public RLock lock(String lockKey, long leaseTime) {
		RLock lock = redissonClient.getFairLock(lockKey);
		lock.lock(leaseTime, TimeUnit.SECONDS);
		return lock;
	}

	/**
	 * 获取并锁定一个公平锁对象，带有租约时间及时区单位
	 *
	 * @param lockKey   锁的唯一键，用于标识锁
	 * @param unit      时间单位
	 * @param leaseTime 锁的租约时间
	 * @return 返回一个带有租约时间及自定义时间单位的已锁定RLock对象
	 */
	@Override
	public RLock lock(String lockKey, TimeUnit unit, long leaseTime) {
		RLock lock = redissonClient.getFairLock(lockKey);
		lock.lock(leaseTime, unit);
		return lock;
	}

	/**
	 * 尝试获取一个公平锁对象，带有等待时间
	 *
	 * @param lockKey  锁的唯一键，用于标识锁
	 * @param unit     时间单位
	 * @param waitTime 等待锁的时间
	 * @return 如果成功获取锁则返回true，否则返回false
	 */
	@Override
	public boolean tryLock(String lockKey, TimeUnit unit, long waitTime) {
		RLock lock = redissonClient.getFairLock(lockKey);
		try {
			return lock.tryLock(waitTime, unit);
		}
		catch (InterruptedException e) {
			return false;
		}
	}

	/**
	 * 尝试获取一个公平锁对象，带有等待时间和租约时间
	 *
	 * @param lockKey   锁的唯一键，用于标识锁
	 * @param unit      时间单位
	 * @param waitTime  等待锁的时间
	 * @param leaseTime 锁的租约时间
	 * @return 如果成功获取锁则返回true，否则返回false
	 */
	@Override
	public boolean tryLock(String lockKey, TimeUnit unit, long waitTime, long leaseTime) {
		RLock lock = redissonClient.getFairLock(lockKey);
		try {
			return lock.tryLock(waitTime, leaseTime, unit);
		}
		catch (InterruptedException e) {
			return false;
		}
	}

	/**
	 * 解锁指定锁键的锁
	 *
	 * @param lockKey 锁的唯一键，用于标识锁
	 */
	@Override
	public void unlock(String lockKey) {
		RLock lock = redissonClient.getFairLock(lockKey);
		lock.unlock();
	}

	/**
	 * 解锁指定的RLock对象
	 *
	 * @param lock 要解锁的RLock对象
	 */
	@Override
	public void unlock(RLock lock) {
		lock.unlock();
	}

}
