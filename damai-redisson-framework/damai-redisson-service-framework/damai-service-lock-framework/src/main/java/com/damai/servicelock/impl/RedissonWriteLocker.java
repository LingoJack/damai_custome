package com.damai.servicelock.impl;

import com.damai.servicelock.ServiceLocker;
import lombok.AllArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

/**
 * RedissonWriteLocker类实现了ServiceLocker接口，用于提供分布式写锁功能。
 * 它利用RedissonClient提供的读写锁实现方法，专注于提供写锁操作。
 */
@AllArgsConstructor
public class RedissonWriteLocker implements ServiceLocker {

    // Redisson客户端，用于与Redis进行交互
    private final RedissonClient redissonClient;

    /**
     * 获取一个公平锁。
     *
     * @param lockKey 锁的键
     * @return RLock对象
     */
    @Override
    public RLock getLock(String lockKey) {
        return redissonClient.getFairLock(lockKey);
    }

    /**
     * 获取一个写锁并加锁。
     *
     * @param lockKey 锁的键
     * @return 加锁后的RLock对象
     */
    @Override
    public RLock lock(String lockKey) {
        RLock lock = redissonClient.getReadWriteLock(lockKey).writeLock();
        lock.lock();
        return lock;
    }

    /**
     * 获取一个写锁并加锁，锁在指定时间后过期。
     *
     * @param lockKey   锁的键
     * @param leaseTime 锁的过期时间（秒）
     * @return 加锁后的RLock对象
     */
    @Override
    public RLock lock(String lockKey, long leaseTime) {
        RLock lock = redissonClient.getReadWriteLock(lockKey).writeLock();
        lock.lock(leaseTime, TimeUnit.SECONDS);
        return lock;
    }

    /**
     * 获取一个写锁并加锁，锁在指定时间后过期。
     *
     * @param lockKey   锁的键
     * @param unit      时间单位
     * @param leaseTime 锁的过期时间
     * @return 加锁后的RLock对象
     */
    @Override
    public RLock lock(String lockKey, TimeUnit unit, long leaseTime) {
        RLock lock = redissonClient.getReadWriteLock(lockKey).writeLock();
        lock.lock(leaseTime, unit);
        return lock;
    }

    /**
     * 尝试获取一个写锁，如果无法获取则返回false。
     *
     * @param lockKey  锁的键
     * @param unit     时间单位
     * @param waitTime 等待时间
     * @return 是否成功获取锁
     */
    @Override
    public boolean tryLock(String lockKey, TimeUnit unit, long waitTime) {
        RLock lock = redissonClient.getReadWriteLock(lockKey).writeLock();
        try {
            return lock.tryLock(waitTime, unit);
        }
        catch (InterruptedException e) {
            return false;
        }
    }

    /**
     * 尝试获取一个写锁，如果无法获取则返回false，锁在指定时间后过期。
     *
     * @param lockKey   锁的键
     * @param unit      时间单位
     * @param waitTime  等待时间
     * @param leaseTime 锁的过期时间
     * @return 是否成功获取锁
     */
    @Override
    public boolean tryLock(String lockKey, TimeUnit unit, long waitTime, long leaseTime) {
        RLock lock = redissonClient.getReadWriteLock(lockKey).writeLock();
        try {
            return lock.tryLock(waitTime, leaseTime, unit);
        }
        catch (InterruptedException e) {
            return false;
        }
    }

    /**
     * 解开指定键的写锁。
     *
     * @param lockKey 锁的键
     */
    @Override
    public void unlock(String lockKey) {
        RLock lock = redissonClient.getReadWriteLock(lockKey).writeLock();
        lock.unlock();
    }

    /**
     * 解开指定的写锁。
     *
     * @param lock 要解开的RLock对象
     */
    @Override
    public void unlock(RLock lock) {
        lock.unlock();
    }

}
