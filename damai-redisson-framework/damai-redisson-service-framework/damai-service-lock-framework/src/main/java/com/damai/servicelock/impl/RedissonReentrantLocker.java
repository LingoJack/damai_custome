package com.damai.servicelock.impl;

import com.damai.servicelock.ServiceLocker;
import lombok.AllArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁 重入锁
 * 本类提供基于Redisson实现的分布式锁，支持多种加锁方式，包括公平锁、非公平锁、带超时的锁等。
 * 主要用于在分布式系统中实现锁机制，以确保在同一时间内只有一个进程可以执行特定的代码块。
 **/
@AllArgsConstructor
public class RedissonReentrantLocker implements ServiceLocker {

	// Redisson客户端，用于连接Redis服务器并操作分布式锁
	private final RedissonClient redissonClient;

	/**
	 * 获取一个分布式锁对象，但不锁定它。
	 *
	 * @param lockKey 锁的唯一键，用于标识锁
	 * @return 返回一个RLock对象，用于后续的锁操作
	 */
	@Override
	public RLock getLock(String lockKey) {
		return redissonClient.getLock(lockKey);
	}

	/**
	 * 获取并锁定一个分布式锁，无限等待直到获取锁成功。
	 *
	 * @param lockKey 锁的唯一键，用于标识锁
	 * @return 返回一个RLock对象，表示已锁定的锁
	 */
	@Override
	public RLock lock(String lockKey) {
		RLock lock = redissonClient.getLock(lockKey);
		lock.lock();
		return lock;
	}

	/**
	 * 获取并锁定一个分布式锁，指定锁的持有时间。
	 *
	 * @param lockKey   锁的唯一键，用于标识锁
	 * @param leaseTime 锁的持有时间，单位为秒
	 * @return 返回一个RLock对象，表示已锁定的锁
	 */
	@Override
	public RLock lock(String lockKey, long leaseTime) {
		RLock lock = redissonClient.getLock(lockKey);
		lock.lock(leaseTime, TimeUnit.SECONDS);
		return lock;
	}

	/**
	 * 获取并锁定一个分布式锁，指定锁的持有时间及时间单位。
	 *
	 * @param lockKey   锁的唯一键，用于标识锁
	 * @param unit      时间单位，用于指定持有时间的计量单位
	 * @param leaseTime 锁的持有时间，根据unit来确定具体时长
	 * @return 返回一个RLock对象，表示已锁定的锁
	 */
	@Override
	public RLock lock(String lockKey, TimeUnit unit, long leaseTime) {
		RLock lock = redissonClient.getLock(lockKey);
		lock.lock(leaseTime, unit);
		return lock;
	}

	/**
	 * 尝试获取一个分布式锁，指定等待时间和时间单位。
	 * 如果在指定时间内无法获取到锁，则返回false。
	 *
	 * @param lockKey  锁的唯一键，用于标识锁
	 * @param unit     时间单位，用于指定等待时间的计量单位
	 * @param waitTime 等待获取锁的时间，根据unit来确定具体时长
	 * @return 如果成功获取到锁，则返回true；否则返回false
	 */
	@Override
	public boolean tryLock(String lockKey, TimeUnit unit, long waitTime) {
		RLock lock = redissonClient.getLock(lockKey);
		try {
			return lock.tryLock(waitTime, unit);
		}
		catch (InterruptedException e) {
			return false;
		}
	}

	/**
	 * 尝试获取一个分布式锁，指定等待时间、持有时间及时间单位。
	 * 如果在指定等待时间内无法获取到锁，则返回false。
	 *
	 * @param lockKey   锁的唯一键，用于标识锁
	 * @param unit      时间单位，用于指定等待时间和持有时间的计量单位
	 * @param waitTime  等待获取锁的时间，根据unit来确定具体时长
	 * @param leaseTime 锁的持有时间，根据unit来确定具体时长
	 * @return 如果成功获取到锁，则返回true；否则返回false
	 */
	@Override
	public boolean tryLock(String lockKey, TimeUnit unit, long waitTime, long leaseTime) {
		RLock lock = redissonClient.getLock(lockKey);
		try {
			return lock.tryLock(waitTime, leaseTime, unit);
		}
		catch (InterruptedException e) {
			return false;
		}
	}

	/**
	 * 解锁指定的锁。
	 *
	 * @param lockKey 锁的唯一键，用于标识锁
	 */
	@Override
	public void unlock(String lockKey) {
		RLock lock = redissonClient.getLock(lockKey);
		lock.unlock();
	}

	/**
	 * 解锁指定的RLock对象。
	 *
	 * @param lock 要解锁的RLock对象
	 */
	@Override
	public void unlock(RLock lock) {
		lock.unlock();
	}
}
