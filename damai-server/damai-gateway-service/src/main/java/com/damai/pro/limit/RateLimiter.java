package com.damai.pro.limit;

import com.damai.enums.BaseCode;
import com.damai.exception.DaMaiFrameException;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 速率限制器，用于控制操作的频率
 * 它使用信号量来限制在特定时间窗口内可以执行的操作次数
 */
public class RateLimiter {

	// 信号量，用于控制并发的许可数量
	private final Semaphore semaphore;
	// 时间单位，用于指定时间窗口的长度
	private final TimeUnit timeUnit;

	/**
	 * 构造函数，初始化速率限制器
	 *
	 * @param maxPermitsPerSecond 每秒允许的最大许可数量
	 */
	public RateLimiter(int maxPermitsPerSecond) {
		// 使用秒作为时间单位
		this.timeUnit = TimeUnit.SECONDS;
		// 初始化信号量，设置初始许可数量
		this.semaphore = new Semaphore(maxPermitsPerSecond);
	}

	/**
	 * 尝试获取一个许可
	 * 如果在指定的时间窗口内没有可用的许可，则抛出异常
	 *
	 * @throws InterruptedException 如果线程被中断
	 */
	public void acquire() throws InterruptedException {
		// 尝试在指定时间窗口内获取一个许可
		if (!semaphore.tryAcquire(1, timeUnit)) {
			// 如果未能获取许可，抛出异常，提示操作过于频繁
			throw new DaMaiFrameException(BaseCode.OPERATION_IS_TOO_FREQUENT_PLEASE_TRY_AGAIN_LATER);
		}
	}

	/**
	 * 释放一个许可
	 * 这通常在操作完成后调用，以释放许可供其他线程使用
	 */
	public void release() {
		// 释放一个许可
		semaphore.release();
	}
}
