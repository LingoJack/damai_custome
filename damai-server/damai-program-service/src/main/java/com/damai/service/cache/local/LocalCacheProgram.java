package com.damai.service.cache.local;

import com.damai.util.DateUtils;
import com.damai.vo.ProgramVo;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import jakarta.annotation.PostConstruct;
import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 节目本地缓存
 **/
@Component
public class LocalCacheProgram {

	/**
	 * 本地缓存
	 */
	private Cache<String, ProgramVo> localCache;

	/**
	 * 本地缓存的容量
	 */
	@Value("${maximumSize:10000}")
	private Long maximumSize;

	/**
	 * 初始化本地缓存
	 */
	@PostConstruct
	public void localLockCacheInit() {
		localCache = Caffeine.newBuilder()
				.maximumSize(maximumSize)
				.expireAfter(new Expiry<String, ProgramVo>() {
					@Override
					public long expireAfterCreate(@NonNull final String key, @NonNull final ProgramVo value,
												  final long currentTime) {
						// 设置缓存项在创建后多久过期
						long duration = DateUtils.countBetweenSecond(DateUtils.now(), value.getShowTime());
						return TimeUnit.MILLISECONDS.toNanos(duration);
					}

					@Override
					public long expireAfterUpdate(@NonNull final String key, @NonNull final ProgramVo value,
												  final long currentTime, @NonNegative final long currentDuration) {
						// 设置缓存项在更新后多久过期
						return currentDuration;
					}

					@Override
					public long expireAfterRead(@NonNull final String key, @NonNull final ProgramVo value,
												final long currentTime, @NonNegative final long currentDuration) {
						// 设置缓存项在读取后多久过期
						return currentDuration;
					}
				})
				.build();
	}

	/**
	 * 获取缓存项，如果缓存中没有则通过function加载
	 * 此方法是线程安全的
	 *
	 * @param id       缓存项的键
	 * @param function 用于加载缓存项的函数
	 * @return 缓存项的值
	 */
	public ProgramVo getCache(String id, Function<String, ProgramVo> function) {
		return localCache.get(id, function);
	}

	/**
	 * 获取缓存项，如果缓存中没有则返回null
	 *
	 * @param id 缓存项的键
	 * @return 缓存项的值，如果没有则返回null
	 */
	public ProgramVo getCache(String id) {
		return localCache.getIfPresent(id);
	}

	/**
	 * 从缓存中删除指定的项
	 *
	 * @param id 要删除的缓存项的键
	 */
	public void del(String id) {
		localCache.invalidate(id);
	}
}
