package com.damai.redis;

import com.damai.core.RedisKeyManage;
import com.damai.core.SpringUtil;
import lombok.Getter;

import java.util.Objects;

/**
 * 基于建造者模式
 * Redis键构建类，用于生成和管理Redis中的键
 */
@Getter
public final class RedisKeyBuild {

	/**
	 * 实际使用的key
	 */
	private final String relKey;

	/**
	 * 私有构造方法，防止外部实例化
	 *
	 * @param relKey 构建完成的Redis键
	 */
	private RedisKeyBuild(String relKey) {
		this.relKey = relKey;
	}

	/**
	 * 构建真实的key
	 *
	 * @param redisKeyManage key的枚举
	 * @param args           占位符的值
	 * @return RedisKeyBuild实例
	 */
	public static RedisKeyBuild createRedisKey(RedisKeyManage redisKeyManage, Object... args) {
		String redisRelKey = String.format(redisKeyManage.getKey(), args);
		return new RedisKeyBuild(SpringUtil.getPrefixDistinctionName() + "-" + redisRelKey);
	}

	/**
	 * 获取Redis键
	 *
	 * @param redisKeyManage key的枚举
	 * @return Redis键字符串
	 */
	public static String getRedisKey(RedisKeyManage redisKeyManage) {
		return SpringUtil.getPrefixDistinctionName() + "-" + redisKeyManage.getKey();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		RedisKeyBuild that = (RedisKeyBuild) o;
		return relKey.equals(that.relKey);
	}

	@Override
	public int hashCode() {
		return Objects.hash(relKey);
	}
}
