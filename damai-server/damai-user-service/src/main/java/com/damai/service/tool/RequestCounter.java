package com.damai.service.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 请求计数器，用于统计和限制在一段时间内的请求次数
 */
@Slf4j
@Component
public class RequestCounter {

	// 计数器，用于记录当前时间窗口内的请求次数，这里使用原子类来防止并发问题
	private final AtomicInteger count = new AtomicInteger(0);

	// 记录最后一次重置计数器的时间
	private final AtomicLong lastResetTime = new AtomicLong(System.currentTimeMillis());

	// 最大请求次数阈值，默认为1000次/秒，可通过Spring配置属性进行设置
	@Value("${request_count_threshold:1000}")
	private int maxRequestsPerSecond = 1000;

	/**
	 * 在请求发生时调用此方法，用于判断是否超过了每秒最大请求次数的限制
	 * 如果超过限制，将记录警告日志并重置计数器
	 *
	 * @return 如果超过每秒最大请求次数，则返回true，否则返回false
	 */
	public synchronized boolean onRequest() {
		// 获取当前时间
		long currentTime = System.currentTimeMillis();

		// 检查是否需要重置计数器
		if (currentTime - lastResetTime.get() >= 1000) {
			count.set(0);
			lastResetTime.set(currentTime);
		}

		// 判断是否超过了每秒最大请求次数
		if (count.incrementAndGet() > maxRequestsPerSecond) {
			// 如果超过，记录警告日志并重置计数器
			log.warn("请求超过每秒{}次限制", maxRequestsPerSecond);
			count.set(0);
			lastResetTime.set(System.currentTimeMillis());
			return true;
		}
		return false;
	}
}
