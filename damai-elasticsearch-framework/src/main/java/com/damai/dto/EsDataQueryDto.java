package com.damai.dto;

import lombok.Data;

import java.util.Date;

/**
 * Elasticsearch查询参数DTO类
 * 用于封装Elasticsearch查询中的参数信息，支持普通字段查询和时间范围查询
 * 并提供是否进行分词查询的选项
 */
@Data
public class EsDataQueryDto {
    /**
     * 字段名
     * 用于指定查询的字段名称
     */
    private String paramName;

    /**
     * 字段值
     * 用于指定查询的字段值，非时间类型字段使用此值进行查询
     */
    private Object paramValue;

    /**
     * 开始时间
     * 用于时间范围查询，指定时间范围的开始时间
     * 当查询字段为时间类型时，与endTime一起使用来定义查询的时间范围
     */
    private Date startTime;

    /**
     * 结束时间
     * 用于时间范围查询，指定时间范围的结束时间
     * 当查询字段为时间类型时，与startTime一起使用来定义查询的时间范围
     */
    private Date endTime;

    /**
     * 是否分词查询标志（默认值为false，即不分词）
     * 当设置为true时，表示在查询时需要对字段值进行分词处理，以支持更复杂的查询需求
     */
    private boolean analyse = false;
}
