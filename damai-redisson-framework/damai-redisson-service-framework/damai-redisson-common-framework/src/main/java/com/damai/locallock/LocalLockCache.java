package com.damai.locallock;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 本地锁缓存
 **/
public class LocalLockCache {

	/**
	 * 本地锁缓存
	 */
	private Cache<String, ReentrantLock> localLockCache;

	/**
	 * 本地锁的过期时间(小时单位)
	 */
	@Value("${durationTime:48}")
	private Integer durationTime;

	@PostConstruct
	public void localLockCacheInit() {
		localLockCache = Caffeine.newBuilder()
				.expireAfterWrite(durationTime, TimeUnit.HOURS)
				.build();
	}

	/**
	 * 获得锁，Caffeine的get是线程安全的
	 */
	public ReentrantLock getLock(String lockKey, boolean fair) {
		return localLockCache.get(lockKey, key -> new ReentrantLock(fair));
	}
}
