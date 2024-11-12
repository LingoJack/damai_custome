package com.damai.servicelock.factory;

import com.damai.core.ManageLocker;
import com.damai.servicelock.LockType;
import com.damai.servicelock.ServiceLocker;
import lombok.AllArgsConstructor;

/**
 * 分布式锁类型工厂
 * <p>
 * 本工厂类用于根据不同的锁类型创建相应的分布式锁实例
 * 通过管理锁的实例ManageLocker来获取不同类型的锁，旨在提供一个简单的接口，
 * 以创建和管理各种分布式锁策略
 */
@AllArgsConstructor
public class ServiceLockFactory {

    // 管理锁的实例，用于获取不同类型的锁
    private final ManageLocker manageLocker;

    /**
     * 根据锁类型获取相应的分布式锁实例
     *
     * @param lockType 锁类型，决定了返回哪种类型的锁
     * @return 返回对应类型的ServiceLocker实例
     * <p>
     * 本方法通过传入的锁类型参数决定创建并返回哪种类型的锁
     * 如果传入的锁类型不匹配任何特定类型，则返回一个重入锁作为默认选项
     */
    public ServiceLocker getLock(LockType lockType) {
        ServiceLocker lock;
        switch (lockType) {
            case Fair:
                // 创建并返回一个公平锁实例
                lock = manageLocker.getFairLocker();
                break;
            case Write:
                // 创建并返回一个写锁实例
                lock = manageLocker.getWriteLocker();
                break;
            case Read:
                // 创建并返回一个读锁实例
                lock = manageLocker.getReadLocker();
                break;
            default:
                // 如果锁类型不匹配，创建并返回一个重入锁实例作为默认选项
                lock = manageLocker.getReentrantLocker();
                break;
        }
        return lock;
    }
}
