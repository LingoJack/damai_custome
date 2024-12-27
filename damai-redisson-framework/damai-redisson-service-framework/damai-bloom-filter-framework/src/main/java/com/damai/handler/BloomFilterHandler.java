package com.damai.handler;

import com.damai.config.BloomFilterProperties;
import com.damai.core.SpringUtil;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;

/**
 * BloomFilterHandler 是一个处理布隆过滤器的类，用于缓存穿透的防护
 * 它通过RedissonClient创建和管理布隆过滤器实例，提供数据添加、查询等功能
 */
public class BloomFilterHandler {

	/**
	 * cachePenetrationBloomFilter 是用于缓存穿透防护的布隆过滤器实例
	 * "penetration" 表示穿透
	 */
	private final RBloomFilter<String> cachePenetrationBloomFilter;

	/**
	 * 构造方法，初始化BloomFilterHandler实例
	 *
	 * @param redissonClient        Redisson客户端，用于连接Redis数据库
	 * @param bloomFilterProperties 布隆过滤器的配置属性，包含过滤器的名称、预期插入数量和误判概率等信息
	 */
	public BloomFilterHandler(RedissonClient redissonClient, BloomFilterProperties bloomFilterProperties) {
		// 创建布隆过滤器实例，使用Spring工具类获取前缀区分名称，并结合配置属性中的名称作为过滤器的标识
		RBloomFilter<String> cachePenetrationBloomFilter = redissonClient.getBloomFilter(
				SpringUtil.getPrefixDistinctionName() + "-" + bloomFilterProperties.getName());
		// 尝试初始化布隆过滤器，传入预期插入数量和误判概率作为参数
		cachePenetrationBloomFilter.tryInit(bloomFilterProperties.getExpectedInsertions(),
				bloomFilterProperties.getFalseProbability());
		this.cachePenetrationBloomFilter = cachePenetrationBloomFilter;
	}

	/**
	 * 向布隆过滤器中添加数据
	 *
	 * @param data 要添加的数据，类型为String
	 * @return 返回添加结果，表示数据是否成功添加到过滤器中
	 */
	public boolean add(String data) {
		return cachePenetrationBloomFilter.add(data);
	}

	/**
	 * 检查布隆过滤器中是否包含指定数据
	 * 如果bloom过滤器不存在数据，则数据一定不存在
	 * 如果bloom过滤器存在该数据，该数据不一定存在
	 *
	 * @param data 要检查的数据，类型为String
	 * @return 返回检查结果，如果过滤器中包含指定数据则返回true，否则返回false
	 */
	public boolean contains(String data) {
		return cachePenetrationBloomFilter.contains(data);
	}

	/**
	 * 获取布隆过滤器的预期插入数量
	 *
	 * @return 返回预期插入数量，表示过滤器设计时预期插入的元素数量
	 */
	public long getExpectedInsertions() {
		return cachePenetrationBloomFilter.getExpectedInsertions();
	}

	/**
	 * 获取布隆过滤器的误判概率
	 *
	 * @return 返回误判概率，表示过滤器在发生误判时的概率
	 */
	public double getFalseProbability() {
		return cachePenetrationBloomFilter.getFalseProbability();
	}

	/**
	 * 获取布隆过滤器的大小
	 *
	 * @return 返回过滤器的大小，表示位数组的长度
	 */
	public long getSize() {
		return cachePenetrationBloomFilter.getSize();
	}

	/**
	 * 获取布隆过滤器的哈希迭代次数
	 *
	 * @return 返回哈希迭代次数，表示在计算哈希值时进行的迭代次数
	 */
	public int getHashIterations() {
		return cachePenetrationBloomFilter.getHashIterations();
	}

	/**
	 * 获取布隆过滤器中当前元素的数量
	 *
	 * @return 返回当前元素数量，表示已经插入过滤器的元素数目
	 */
	public long count() {
		return cachePenetrationBloomFilter.count();
	}
}
