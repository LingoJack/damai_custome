package com.damai.servicelock.info;

/**
 * 分布式锁超时策略枚举
 * 定义了当获取锁超时时的处理策略。当前实现包括：
 * <p>
 * - FAIL：当锁获取失败时，触发频繁请求的异常。
 * <p>
 * 每个枚举项需要实现 LockTimeOutHandler 接口的 handler 方法，
 * 用于在锁超时时执行相应的处理逻辑。
 **/
public enum LockTimeOutStrategy implements LockTimeOutHandler {
    /**
     * 锁获取失败策略
     * 处理逻辑：当锁获取失败时，抛出 RuntimeException 异常，
     * 提示请求频繁。
     */
    FAIL() {
        @Override
        public void handler(String lockName) {
            String msg = String.format("%s请求频繁", lockName);
            throw new RuntimeException(msg);
        }
    }
}
