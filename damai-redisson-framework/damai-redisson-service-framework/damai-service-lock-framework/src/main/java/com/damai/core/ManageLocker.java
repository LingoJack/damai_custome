package com.damai.core;

import com.damai.servicelock.LockType;
import com.damai.servicelock.ServiceLocker;
import com.damai.servicelock.impl.RedissonFairLocker;
import com.damai.servicelock.impl.RedissonReadLocker;
import com.damai.servicelock.impl.RedissonReentrantLocker;
import com.damai.servicelock.impl.RedissonWriteLocker;
import org.redisson.api.RedissonClient;

import java.util.HashMap;
import java.util.Map;

import static com.damai.servicelock.LockType.Fair;
import static com.damai.servicelock.LockType.Read;
import static com.damai.servicelock.LockType.Reentrant;
import static com.damai.servicelock.LockType.Write;

/**
 * 分布式锁 锁缓存
 * 本类负责根据不同的锁类型缓存对应的分布式锁实现
 */
public class ManageLocker {

    /**
     * 锁类型与服务锁实现的映射缓存
     * 你从这里都是getter方法也可以看出来，这一切只会在初始化时写入一次，后续都是只读操作，因为该字段被声明为final
     * 在只读环境下，HashMap是线程安全的，因此这里可以不使用ConcurrentHashMap
     */
    private final Map<LockType, ServiceLocker> cacheLocker = new HashMap<>();

    /**
     * 构造方法
     * 初始化不同锁类型的锁实例并存入缓存
     *
     * @param redissonClient Redisson客户端，用于操作Redis
     */
    public ManageLocker(RedissonClient redissonClient) {
        cacheLocker.put(Reentrant, new RedissonReentrantLocker(redissonClient));
        cacheLocker.put(Fair, new RedissonFairLocker(redissonClient));
        cacheLocker.put(Write, new RedissonWriteLocker(redissonClient));
        cacheLocker.put(Read, new RedissonReadLocker(redissonClient));
    }

    /**
     * 获取可重入锁的实现
     *
     * @return 可重入锁的实现
     */
    public ServiceLocker getReentrantLocker() {
        return cacheLocker.get(Reentrant);
    }

    /**
     * 获取公平锁的实现
     *
     * @return 公平锁的实现
     */
    public ServiceLocker getFairLocker() {
        return cacheLocker.get(Fair);
    }

    /**
     * 获取写锁的实现
     *
     * @return 写锁的实现
     */
    public ServiceLocker getWriteLocker() {
        return cacheLocker.get(Write);
    }

    /**
     * 获取读锁的实现
     *
     * @return 读锁的实现
     */
    public ServiceLocker getReadLocker() {
        return cacheLocker.get(Read);
    }
}
