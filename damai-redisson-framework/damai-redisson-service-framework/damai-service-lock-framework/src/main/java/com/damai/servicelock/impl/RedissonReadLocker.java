package com.damai.servicelock.impl;

import com.damai.servicelock.ServiceLocker;
import lombok.AllArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁 读锁
 * 用于在分布式环境中对资源进行读取操作时加锁，以保证数据的一致性
 */
@AllArgsConstructor
public class RedissonReadLocker implements ServiceLocker {

    // Redisson客户端，用于连接Redis服务器并操作锁
    private final RedissonClient redissonClient;

    /**
     * 获取锁对象，但不加锁
     *
     * @param lockKey 锁的键值
     * @return 返回一个RLock锁对象
     */
    @Override
    public RLock getLock(String lockKey) {
        return redissonClient.getReadWriteLock(lockKey).readLock();
    }

    /**
     * 加读锁，适用于读操作
     *
     * @param lockKey 锁的键值
     * @return 返回加锁后的RLock对象
     */
    @Override
    public RLock lock(String lockKey) {
        RLock lock = redissonClient.getReadWriteLock(lockKey).readLock();
        lock.lock();
        return lock;
    }

    /**
     * 加读锁，适用于读操作，并指定锁的持有时间
     *
     * @param lockKey   锁的键值
     * @param leaseTime 锁的持有时间
     * @return 返回加锁后的RLock对象
     */
    @Override
    public RLock lock(String lockKey, long leaseTime) {
        RLock lock = redissonClient.getReadWriteLock(lockKey).readLock();
        lock.lock(leaseTime, TimeUnit.SECONDS);
        return lock;
    }

    /**
     * 加读锁，适用于读操作，并指定锁的持有时间及时间单位
     *
     * @param lockKey   锁的键值
     * @param unit      时间单位
     * @param leaseTime 锁的持有时间
     * @return 返回加锁后的RLock对象
     */
    @Override
    public RLock lock(String lockKey, TimeUnit unit, long leaseTime) {
        RLock lock = redissonClient.getReadWriteLock(lockKey).readLock();
        lock.lock(leaseTime, unit);
        return lock;
    }

    /**
     * 尝试加读锁，适用于读操作，如果无法加锁则返回false
     *
     * @param lockKey  锁的键值
     * @param unit     时间单位
     * @param waitTime 等待加锁的时间
     * @return 是否成功加锁
     */
    @Override
    public boolean tryLock(String lockKey, TimeUnit unit, long waitTime) {
        RLock lock = redissonClient.getReadWriteLock(lockKey).readLock();
        try {
            return lock.tryLock(waitTime, unit);
        }
        catch (InterruptedException e) {
            return false;
        }
    }

    /**
     * 尝试加读锁，适用于读操作，如果无法加锁则返回false，并指定锁的持有时间
     *
     * @param lockKey   锁的键值
     * @param unit      时间单位
     * @param waitTime  等待加锁的时间
     * @param leaseTime 锁的持有时间
     * @return 是否成功加锁
     */
    @Override
    public boolean tryLock(String lockKey, TimeUnit unit, long waitTime, long leaseTime) {
        RLock lock = redissonClient.getReadWriteLock(lockKey).readLock();
        try {
            return lock.tryLock(waitTime, leaseTime, unit);
        }
        catch (InterruptedException e) {
            return false;
        }
    }

    /**
     * 解除读锁，根据锁的键值
     *
     * @param lockKey 锁的键值
     */
    @Override
    public void unlock(String lockKey) {
        RLock lock = redissonClient.getReadWriteLock(lockKey).readLock();
        lock.unlock();
    }

    /**
     * 解除读锁，直接传入锁对象
     *
     * @param lock 锁对象
     */
    @Override
    public void unlock(RLock lock) {
        lock.unlock();
    }

}
