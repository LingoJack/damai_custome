package com.damai.shardingsphere;

import cn.hutool.core.collection.CollectionUtil;
import com.damai.enums.BaseCode;
import com.damai.exception.DaMaiFrameException;
import org.apache.shardingsphere.sharding.api.sharding.complex.ComplexKeysShardingAlgorithm;
import org.apache.shardingsphere.sharding.api.sharding.complex.ComplexKeysShardingValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * 自定义的分表算法
 * 实现一个复杂的基因分片算法类，用于处理表格的分片逻辑
 * 该类主要根据订单号或用户ID进行分片，以实现数据的水平分区
 * 被用于在ShardingSphere配置文件
 */
public class TableOrderComplexGeneArithmetic implements ComplexKeysShardingAlgorithm<Long> {

	// 分片总数的配置键名
	private static final String SHARDING_COUNT_KEY_NAME = "sharding-count";

	// 存储分片总数
	private int shardingCount;

	/**
	 * 初始化方法，从属性配置中读取分片总数
	 *
	 * @param props 配置属性，包含分片总数
	 */
	@Override
	public void init(Properties props) {
		shardingCount = Integer.parseInt(props.getProperty(SHARDING_COUNT_KEY_NAME));
	}

	/**
	 * 执行分片逻辑的方法，根据提供的分片值决定数据应归属的表。
	 * 分表算法的执行流程：
	 * 1. 获取逻辑表名，即开发中SQL的表名（例如：d_order），用于后续真实表的构成。
	 * 2. 从 `complexKeysShardingValue` 中获取 `columnNameAndShardingValuesMap`，其中 key 为要查询或操作数据的字段名，value 为具体的值集合。
	 * 3. 从 `columnNameAndShardingValuesMap` 中分别提取 `order_number` 和 `user_id` 的值，哪个存在就使用哪个值。
	 * 4. 使用提取的值对 `shardingCount`（分表数量）进行取模运算。通过分片基因法替换后，订单编号取模后的值与用户ID取模后的值相同。
	 * 5. 取模结果作为分片的索引位置。
	 * 6. 将逻辑表名与获取的索引值拼接成真实的表名。
	 * 7. 返回真实的表名。
	 * 8. 如果操作中的条件没有 `order_number` 或 `user_id` 的分片键，则返回所有真实表名，进行全路由读取。
	 *
	 * @param allActualSplitTableNames 所有可能的分片表名集合
	 * @param complexKeysShardingValue 包含分片列和对应值的复杂分片值对象
	 * @return 返回选定的分片表名集合
	 */
	@Override
	public Collection<String> doSharding(Collection<String> allActualSplitTableNames, ComplexKeysShardingValue<Long> complexKeysShardingValue) {

		// 返回的真实表名集合
		List<String> actualTableNames = new ArrayList<>(allActualSplitTableNames.size());

		// 获取逻辑表名
		String logicTableName = complexKeysShardingValue.getLogicTableName();

		// 查询中的列名和值
		Map<String, Collection<Long>> columnNameAndShardingValuesMap = complexKeysShardingValue.getColumnNameAndShardingValuesMap();

		// 如果没有条件查询，那么就查所有的分表
		if (CollectionUtil.isEmpty(columnNameAndShardingValuesMap)) {
			return allActualSplitTableNames;
		}

		// 分别获取订单号和用户ID的分片值集合
		Collection<Long> orderNumberValues = columnNameAndShardingValuesMap.get("order_number");
		Collection<Long> userIdValues = columnNameAndShardingValuesMap.get("user_id");

		// 分片键的值
		Long value = null;

		if (CollectionUtil.isNotEmpty(orderNumberValues)) {
			// 如果是order_number查询，即订单号分片值集合非空，取第一个订单号作为分片值
			value = orderNumberValues.stream().findFirst().orElseThrow(() -> new DaMaiFrameException(BaseCode.ORDER_NUMBER_NOT_EXIST));
		}
		else if (CollectionUtil.isNotEmpty(userIdValues)) {
			// 如果是user_id查询，即用户ID分片值集合非空，取第一个用户ID作为分片值
			value = userIdValues.stream().findFirst().orElseThrow(() -> new DaMaiFrameException(BaseCode.USER_ID_NOT_EXIST));
		}

		// 如果order_number或者user_id的值存在
		if (Objects.nonNull(value)) {
			// 逻辑表名_分片键的值对分表数量进行取模
			// 借鉴HashMap源码的位运算取模操作，由于是二进制按位与操作，所以效率非常快
			actualTableNames.add(logicTableName + "_" + ((shardingCount - 1) & value));
			return actualTableNames;
		}

		// 如果没有分片键查询，则把所有真实表返回
		return allActualSplitTableNames;
	}
}

