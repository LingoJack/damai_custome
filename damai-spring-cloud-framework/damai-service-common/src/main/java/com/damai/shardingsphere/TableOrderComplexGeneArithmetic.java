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
 * 实现一个复杂的分片算法类，用于处理表格的分片逻辑
 * 该类主要根据订单号或用户ID进行分片，以实现数据的水平分区
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
     * 执行分片逻辑的方法，根据提供的分片值决定数据应归属的表
     * 分表算法的执行流程
     *
     * @param allActualSplitTableNames 所有可能的分片表名集合
     * @param complexKeysShardingValue 包含分片列和对应值的复杂分片值对象
     * @return 返回选定的分片表名集合
     */
    @Override
    public Collection<String> doSharding(Collection<String> allActualSplitTableNames, ComplexKeysShardingValue<Long> complexKeysShardingValue) {

        // 初始化实际表名列表，大小与所有可能的分片表名集合相同
        List<String> actualTableNames = new ArrayList<>(allActualSplitTableNames.size());

        // 获取逻辑表名
        String logicTableName = complexKeysShardingValue.getLogicTableName();

        // 获取分片列名和对应的分片值映射
        Map<String, Collection<Long>> columnNameAndShardingValuesMap = complexKeysShardingValue.getColumnNameAndShardingValuesMap();

        // 如果分片列值映射为空，则直接返回所有可能的分片表名集合
        if (CollectionUtil.isEmpty(columnNameAndShardingValuesMap)) {
            return allActualSplitTableNames;
        }

        // 分别获取订单号和用户ID的分片值集合
        Collection<Long> orderNumberValues = columnNameAndShardingValuesMap.get("order_number");
        Collection<Long> userIdValues = columnNameAndShardingValuesMap.get("user_id");

        Long value = null;

        if (CollectionUtil.isNotEmpty(orderNumberValues)) {
            // 如果订单号分片值集合非空，取第一个订单号作为分片值
            value = orderNumberValues.stream().findFirst().orElseThrow(() -> new DaMaiFrameException(BaseCode.ORDER_NUMBER_NOT_EXIST));
        }
        else if (CollectionUtil.isNotEmpty(userIdValues)) {
            // 如果用户ID分片值集合非空，取第一个用户ID作为分片值
            value = userIdValues.stream().findFirst().orElseThrow(() -> new DaMaiFrameException(BaseCode.USER_ID_NOT_EXIST));
        }

        // 如果分片值存在，则根据分片值和分片总数计算出实际表名，并添加到实际表名列表中
        if (Objects.nonNull(value)) {
            // 借鉴HashMap源码的取模操作，由于是二进制按位与操作，所以效率非常快
            actualTableNames.add(logicTableName + "_" + ((shardingCount - 1) & value));
            return actualTableNames;
        }

        // 如果分片值不存在，则返回所有可能的分片表名集合
        return allActualSplitTableNames;
    }
}

