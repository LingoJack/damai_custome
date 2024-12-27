package com.damai.shardingsphere;

import cn.hutool.core.collection.CollectionUtil;
import com.damai.enums.BaseCode;
import com.damai.exception.DaMaiFrameException;
import com.damai.util.StringUtil;
import org.apache.shardingsphere.sharding.api.sharding.complex.ComplexKeysShardingAlgorithm;
import org.apache.shardingsphere.sharding.api.sharding.complex.ComplexKeysShardingValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * 自定义的分库算法
 * 实现基于数据库顺序的复杂基因算法类，用于处理订单相关的数据库分片。
 * 该类实现了ComplexKeysShardingAlgorithm接口，用于根据多个键值进行数据库分片。
 */
public class DatabaseOrderComplexGeneArithmetic implements ComplexKeysShardingAlgorithm<Long> {

	// 分片总数的配置键名
	private static final String SHARDING_COUNT_KEY_NAME = "sharding-count";

	// 表分片总数的配置键名
	private static final String TABLE_SHARDING_COUNT_KEY_NAME = "table-sharding-count";

	// 数据库分片总数
	private int shardingCount;

	// 表分片总数
	private int tableShardingCount;

	/**
	 * 初始化方法，从配置属性中读取分片总数和表分片总数。
	 *
	 * @param props 包含配置属性的Properties对象
	 */
	@Override
	public void init(Properties props) {
		this.shardingCount = Integer.parseInt(props.getProperty(SHARDING_COUNT_KEY_NAME));
		this.tableShardingCount = Integer.parseInt(props.getProperty(TABLE_SHARDING_COUNT_KEY_NAME));
	}

	/**
	 * 根据复杂键值进行数据库分片
	 * 该方法用于根据订单号或用户ID来选择实际的数据库名称
	 *
	 * @param allActualSplitDatabaseNames 所有可能的数据库名称集合
	 * @param complexKeysShardingValue    包含分片键值的复杂键分片值对象
	 * @return 返回选中的数据库名称集合
	 */
	public Collection<String> doSharding(Collection<String> allActualSplitDatabaseNames, ComplexKeysShardingValue<Long> complexKeysShardingValue) {
		// 初始化实际数据库名称列表，即最后要返回的结果
		List<String> actualDatabaseNames = new ArrayList<>(allActualSplitDatabaseNames.size());

		// 获取列名和分片值的映射
		Map<String, Collection<Long>> columnNameAndShardingValuesMap = complexKeysShardingValue.getColumnNameAndShardingValuesMap();

		// 如果映射为空，则直接返回空列表
		if (CollectionUtil.isEmpty(columnNameAndShardingValuesMap)) {
			return actualDatabaseNames;
		}

		// 获取订单号和用户ID对应的分片值集合
		Collection<Long> orderNumberValues = columnNameAndShardingValuesMap.get("order_number");
		Collection<Long> userIdValues = columnNameAndShardingValuesMap.get("user_id");

		Long value = null;

		if (CollectionUtil.isNotEmpty(orderNumberValues)) {
			// 如果订单号分片值集合非空，取第一个值作为分片依据
			value = orderNumberValues.stream().findFirst().orElseThrow(() -> new DaMaiFrameException(BaseCode.ORDER_NUMBER_NOT_EXIST));
		}
		else if (CollectionUtil.isNotEmpty(userIdValues)) {
			// 如果用户ID分片值集合非空，取第一个值作为分片依据
			value = userIdValues.stream().findFirst().orElseThrow(() -> new DaMaiFrameException(BaseCode.USER_ID_NOT_EXIST));
		}

		if (Objects.nonNull(value)) {
			// 如果获取到了分片值，则计算数据库索引并选择对应的数据库名称
			long databaseIndex = calculateDatabaseIndex(shardingCount, value, tableShardingCount);
			String databaseIndexStr = String.valueOf(databaseIndex);

			for (String actualSplitDatabaseName : allActualSplitDatabaseNames) {
				// 如果数据库名称包含计算出的索引，则添加到实际数据库名称列表中
				if (actualSplitDatabaseName.contains(databaseIndexStr)) {
					actualDatabaseNames.add(actualSplitDatabaseName);
					break;
				}
			}
			return actualDatabaseNames;
		}
		else {
			// 如果未获取到分片值，则返回所有可能的数据库名称集合
			return allActualSplitDatabaseNames;
		}
	}


	/**
	 * 计算给定表索引应分配到的数据库编号。
	 * 1 首先将分片键(订单编号或者用户id)转换成二进制字符串
	 * 2 找到被替换基因的长度，这里计算tableCount分表数量的log2n对数
	 * 3 获取到长度后，根据长度开始截取二进制的分片键，获得被替换后的基因
	 * 4 对截取到的基因字符串的hashcode进行优化，使其分布更加均匀
	 * 5 对分库数量进行取模
	 *
	 * @param databaseCount 数据库总数
	 * @param splicingKey   分片键
	 * @param tableCount    表总数
	 * @return 分配到的数据库编号
	 */
	public long calculateDatabaseIndex(Integer databaseCount, Long splicingKey, Integer tableCount) {
		// 将分片键转换为二进制字符串
		String splicingKeyBinary = Long.toBinaryString(splicingKey);
		// 计算表总数的对数，用于确定二进制字符串的截取长度
		long replacementLength = log2N(tableCount);
		// 截取分片键二进制字符串的尾部，长度为表总数的以2为底的对数
		String geneBinaryStr = splicingKeyBinary.substring(splicingKeyBinary.length() - (int) replacementLength);

		// 如果截取到的二进制字符串不为空
		if (StringUtil.isNotEmpty(geneBinaryStr)) {
			int h;
			// 计算字符串的哈希码，右移十六位再进行异或运算，以优化以提高均匀分布
			int geneOptimizeHashCode = (h = geneBinaryStr.hashCode()) ^ (h >>> 16);
			// 使用按位与运算根据数据库总数和优化后的哈希码取模计算数据库编号
			return (databaseCount - 1) & geneOptimizeHashCode;
		}

		// 如果截取到的二进制字符串为空，抛出异常表示未找到有效的基因
		throw new DaMaiFrameException(BaseCode.NOT_FOUND_GENE);
	}


	/**
	 * 计算以2为底的对数函数。
	 *
	 * @param count 计算对数的数值
	 * @return 对数计算结果
	 */
	public long log2N(long count) {
		return (long) (Math.log(count) / Math.log(2));
	}
}

