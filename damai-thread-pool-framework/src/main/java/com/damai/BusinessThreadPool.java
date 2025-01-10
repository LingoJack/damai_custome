package com.damai;


import com.damai.base.BaseThreadPool;
import com.damai.namefactory.BusinessNameThreadFactory;
import com.damai.rejectedexecutionhandler.ThreadPoolRejectedExecutionHandler;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.*;

/**
 * 线程池
 **/
public class BusinessThreadPool extends BaseThreadPool {
	// 线程池执行器
	private static ThreadPoolExecutor execute = null;

	// 静态初始化块，用于初始化线程池
	static {
		// 创建线程池执行器实例
		execute = new ThreadPoolExecutor(
				// 核心线程数：当前设备的可用处理器数量加一，旨在充分利用CPU资源
				Runtime.getRuntime().availableProcessors() + 1,
				// 最大线程数：调用自定义方法maximumPoolSize()确定
				maximumPoolSize(),
				// 空闲线程存活时间
				60,
				// 时间单位：秒
				TimeUnit.SECONDS,
				// 工作队列：使用ArrayBlockingQueue，固定大小为600，用于暂存等待执行的任务
				new ArrayBlockingQueue<>(600),
				// 线程工厂：使用自定义的BusinessNameThreadFactory为线程池中的线程命名
				new BusinessNameThreadFactory(),
				// 拒绝策略：当任务无法提交时，使用自定义的AbortPolicy进行处理
				new ThreadPoolRejectedExecutionHandler.BusinessAbortPolicy());
	}

	private static Integer maximumPoolSize() {
		return new BigDecimal(Runtime.getRuntime().availableProcessors())
				.divide(new BigDecimal("0.2"), 0, RoundingMode.HALF_UP).intValue();
	}

	public static void execute(Runnable r) {
		execute.execute(wrapTask(r, getContextForTask(), getContextForHold()));
	}

	public static <T> Future<T> submit(Callable<T> c) {
		return execute.submit(wrapTask(c, getContextForTask(), getContextForHold()));
	}
}
