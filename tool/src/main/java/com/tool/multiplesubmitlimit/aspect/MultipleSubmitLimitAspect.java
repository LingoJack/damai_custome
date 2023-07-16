package com.tool.multiplesubmitlimit.aspect;

import com.tool.multiplesubmitlimit.annotion.MultipleSubmitLimit;
import com.tool.multiplesubmitlimit.info.MultipleSubmitLimitInfoProvider;
import com.tool.multiplesubmitlimit.info.MultipleSubmitLimitRejectedStrategy;
import com.tool.multiplesubmitlimit.info.strategy.repeatrejected.MultipleSubmitLimitHandler;
import com.tool.multiplesubmitlimit.info.strategy.repeatrejected.MultipleSubmitLimitStrategyFactory;
import com.tool.servicelock.ServiceLocker;
import com.tool.servicelock.redisson.LockType;
import com.tool.servicelock.redisson.factory.ServiceLockFactory;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

import java.util.concurrent.TimeUnit;

/**
 * @program: redis-tool
 * @description: 防重复提交限制功能
 * @author: 星哥
 * @create: 2023-05-28
 **/
@Aspect
@Order(-9)
@Slf4j
public class MultipleSubmitLimitAspect {

    private final long MAX_TIME_OUT = 15;

    private final long WAIT_TIME = 0;

    
    private final MultipleSubmitLimitInfoProvider repeatLimitInfoProvider;

    
    private final ServiceLockFactory serviceLockFactory;

    
    private final MultipleSubmitLimitStrategyFactory repeatLimitStrategyFactory;
    
    public MultipleSubmitLimitAspect(MultipleSubmitLimitInfoProvider repeatLimitInfoProvider, ServiceLockFactory serviceLockFactory,
                                     MultipleSubmitLimitStrategyFactory repeatLimitStrategyFactory){
        this.repeatLimitInfoProvider = repeatLimitInfoProvider;
        this.serviceLockFactory = serviceLockFactory;
        this.repeatLimitStrategyFactory = repeatLimitStrategyFactory;
    }

    @Around("@annotation(repeatLimit)")
    public Object around(ProceedingJoinPoint joinPoint, MultipleSubmitLimit repeatLimit) throws Throwable {
        Object o = null;
        String lockName = "";
        String resultKeyName = "";
        String[] keys = repeatLimit.keys();
        String name = repeatLimit.name();
        long timeout = checkTimeOut(repeatLimit.timeout());
        MultipleSubmitLimitRejectedStrategy repeatRejectedStrategy = repeatLimit.repeatRejected();
        if (keys != null && keys.length > 0) {
            lockName = repeatLimitInfoProvider.getLockName(joinPoint,name, keys);
            resultKeyName = repeatLimitInfoProvider.getResultKeyName(joinPoint,name, keys);
        }else{
            lockName = repeatLimitInfoProvider.getLockNameByGenerateKeyStrategy(repeatLimit, joinPoint);
            resultKeyName = repeatLimitInfoProvider.getResultKeyNameByGenerateKeyStrategy(repeatLimit, joinPoint);
        }

        log.info("==repeat limit lockName:{} resultKeyName:{}==",lockName,resultKeyName);
        MultipleSubmitLimitHandler repeatLimitHandler = repeatLimitStrategyFactory.getMultipleSubmitLimitStrategy(repeatRejectedStrategy.getMsg());

        ServiceLocker lock = serviceLockFactory.createLock(LockType.Reentrant);
        boolean result = false;
        if (timeout == 0) {
            result = lock.tryLock(lockName, TimeUnit.SECONDS, WAIT_TIME);
        }else {
            result = lock.tryLock(lockName, TimeUnit.SECONDS, WAIT_TIME, timeout);
        }
        //加锁成功执行
        if (result) {
            try{
                o = repeatLimitHandler.execute(resultKeyName, timeout,TimeUnit.SECONDS,joinPoint);
                return o;
            }finally {
                lock.unlock(lockName);
            }
        //加锁没有成功
        }else{
            o = repeatLimitHandler.repeatRejected(resultKeyName);
            return o;
        }
    }

    public long checkTimeOut(long timeout){
        if (timeout > MAX_TIME_OUT) {
            timeout = MAX_TIME_OUT;
        }
        return timeout;
    }
}
