package com.damai.util;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.damai.dto.EsDataQueryDto;
import com.damai.dto.EsDocumentMappingDto;
import com.damai.dto.EsGeoPointDto;
import com.damai.dto.EsGeoPointSortDto;
import com.github.pagehelper.PageInfo;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.common.geo.GeoDistance;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.GeoDistanceQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.GeoDistanceSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * BusinessEsHandle类是用于处理与业务相关的Elasticsearch操作的封装
 * 提供了与Elasticsearch数据库交互的方法，以便对数据进行查询、更新等操作
 */
@Slf4j
@AllArgsConstructor
public class BusinessEsHandle {

    /**
     * 用于与Elasticsearch进行网络通信
     */
    private final RestClient restClient;

    /**
     * 用于控制Elasticsearch操作的开关
     */
    private final Boolean esSwitch;

    /**
     * 用于控制Elasticsearch中索引类型的开关
     */
    private final Boolean esTypeSwitch;

    /**
     * 创建索引
     *
     * @param indexName 索引名字
     * @param indexType 索引类型
     * @param list      参数集合
     */
    public void createIndex(String indexName, String indexType, List<EsDocumentMappingDto> list) throws IOException {
        if (!esSwitch) {
            return;
        }
        if (CollectionUtil.isEmpty(list)) {
            return;
        }
        IndexRequest indexRequest = new IndexRequest();
        XContentBuilder builder = JsonXContent.contentBuilder().startObject().startObject("mappings");
        if (esTypeSwitch) {
            builder = builder.startObject(indexType);
        }
        builder = builder.startObject("properties");
        for (EsDocumentMappingDto esDocumentMappingDto : list) {
            String paramName = esDocumentMappingDto.getParamName();
            String paramType = esDocumentMappingDto.getParamType();
            if ("text".equals(paramType)) {
                Map<String, Map<String, Object>> map1 = new HashMap<>(8);
                Map<String, Object> map2 = new HashMap<>(8);
                map2.put("type", "keyword");
                map2.put("ignore_above", 256);
                map1.put("keyword", map2);
                builder = builder.startObject(paramName).field("type", "text").field("fields", map1).endObject();
            }
            else {
                builder = builder.startObject(paramName).field("type", paramType).endObject();
            }
        }
        if (esTypeSwitch) {
            builder.endObject();
        }
        builder = builder.endObject().endObject().startObject("settings").field("number_of_shards", 3)
                .field("number_of_replicas", 1).endObject().endObject();

        indexRequest.source(builder);
        String source = indexRequest.source().utf8ToString();
        log.info("create index execute dsl : {}", source);
        HttpEntity entity = new NStringEntity(source, ContentType.APPLICATION_JSON);
        Request request = new Request("PUT", "/" + indexName);
        request.setEntity(entity);
        request.addParameters(Collections.<String, String>emptyMap());
        Response performRequest = restClient.performRequest(request);
    }

    /**
     * 检查索引是否存在
     *
     * @param indexName 索引名字
     * @param indexType 索引类型
     * @return boolean
     */
    public boolean checkIndex(String indexName, String indexType) {
        if (!esSwitch) {
            return false;
        }
        try {
            String path = "";
            if (esTypeSwitch) {
                path = "/" + indexName + "/" + indexType + "/_mapping?include_type_name";
            }
            else {
                path = "/" + indexName + "/_mapping";
            }
            Request request = new Request("GET", path);
            request.addParameters(Collections.<String, String>emptyMap());
            Response response = restClient.performRequest(request);
            String result = EntityUtils.toString(response.getEntity());
            System.out.println(JSON.toJSONString(result));
            return "OK".equals(response.getStatusLine().getReasonPhrase());
        }
        catch (Exception e) {
            if (e instanceof ResponseException && ((ResponseException) e).getResponse().getStatusLine().getStatusCode() == RestStatus.NOT_FOUND.getStatus()) {
                log.warn("index not exist ! indexName:{}, indexType:{}", indexName, indexType);
            }
            else {
                log.error("checkIndex error", e);
            }
            return false;
        }
    }

    /**
     * 删除索引
     *
     * @param indexName 索引名字
     * @return boolean
     */
    public boolean deleteIndex(String indexName) {
        if (!esSwitch) {
            return false;
        }
        try {
            Request request = new Request("DELETE", "/" + indexName);
            request.addParameters(Collections.<String, String>emptyMap());
            Response response = restClient.performRequest(request);
            return "OK".equals(response.getStatusLine().getReasonPhrase());
        }
        catch (Exception e) {
            log.error("deleteIndex error", e);
        }
        return false;
    }

    /**
     * 清空索引下所有数据
     *
     * @param indexName 索引名字
     */
    public void deleteData(String indexName) {
        if (!esSwitch) {
            return;
        }
        deleteIndex(indexName);
    }

    /**
     * 添加
     *
     * @param indexName 索引名字
     * @param indexType 索引类型
     * @param params    参数 key:字段名 value:具体值
     * @return boolean
     */
    public boolean add(String indexName, String indexType, Map<String, Object> params) {
        return add(indexName, indexType, params, null);
    }

    /**
     * 添加
     *
     * @param indexName 索引名字
     * @param indexType 索引类型
     * @param params    参数 key:字段名 value:具体值
     * @param id        文档id 如果为空，则使用es默认id
     * @return boolean
     */
    public boolean add(String indexName, String indexType, Map<String, Object> params, String id) {
        if (!esSwitch) {
            return false;
        }
        if (CollectionUtil.isEmpty(params)) {
            return false;
        }
        try {
            String jsonString = JSON.toJSONString(params);
            HttpEntity entity = new NStringEntity(jsonString, ContentType.APPLICATION_JSON);
            String endpoint = "";
            if (esTypeSwitch) {
                endpoint = "/" + indexName + "/" + indexType;
            }
            else {
                endpoint = "/" + indexName + "/_doc";
            }
            if (StringUtil.isNotEmpty(id)) {
                endpoint = endpoint + "/" + id;
            }
            log.info("add dsl : {}", jsonString);
            Request request = new Request("POST", endpoint);
            request.setEntity(entity);
            request.addParameters(Collections.<String, String>emptyMap());
            Response indexResponse = restClient.performRequest(request);
            String reasonPhrase = indexResponse.getStatusLine().getReasonPhrase();
            return "created".equalsIgnoreCase(reasonPhrase) || "ok".equalsIgnoreCase(reasonPhrase);
        }
        catch (Exception e) {
            log.error("add error", e);
        }
        return false;
    }

    /**
     * 查询
     *
     * @param indexName          索引名字
     * @param indexType          索引类型
     * @param esDataQueryDtoList 参数
     * @param clazz              返回的类型
     * @return List
     */
    public <T> List<T> query(String indexName, String indexType, List<EsDataQueryDto> esDataQueryDtoList, Class<T> clazz) throws IOException {
        if (!esSwitch) {
            return new ArrayList<>();
        }
        return query(indexName, indexType, null, esDataQueryDtoList, null, null, null, null, null, clazz);
    }

    /**
     * 查询
     *
     * @param indexName          索引名字
     * @param indexType          索引类型
     * @param esGeoPointDto      经纬度查询参数
     * @param esDataQueryDtoList 参数
     * @param clazz              返回的类型
     * @return List
     */
    public <T> List<T> query(String indexName, String indexType, EsGeoPointDto esGeoPointDto, List<EsDataQueryDto> esDataQueryDtoList, Class<T> clazz) throws IOException {
        if (!esSwitch) {
            return new ArrayList<>();
        }
        return query(indexName, indexType, esGeoPointDto, esDataQueryDtoList, null, null, null, null, null, clazz);
    }

    /**
     * 查询
     *
     * @param indexName          索引名字
     * @param indexType          索引类型
     * @param esDataQueryDtoList 参数
     * @param sortParam          普通参数排序 不排序则为空 如果进行了排序，会返回es中的排序字段sort，需要用户在返回的实体类中添加sort字段
     * @param sortOrder          升序还是降序，为空则降序
     * @param clazz              返回的类型
     * @return List
     */
    public <T> List<T> query(String indexName, String indexType, List<EsDataQueryDto> esDataQueryDtoList, String sortParam, SortOrder sortOrder, Class<T> clazz) throws IOException {
        if (!esSwitch) {
            return new ArrayList<>();
        }
        return query(indexName, indexType, null, esDataQueryDtoList, sortParam, null, sortOrder, null, null, clazz);
    }

    /**
     * 查询
     *
     * @param indexName            索引名字
     * @param indexType            索引类型
     * @param esDataQueryDtoList   参数
     * @param geoPointDtoSortParam 经纬度參數排序 不排序则为空 如果进行了排序，会返回es中的排序字段sort，需要用户在返回的实体类中添加sort字段
     * @param sortOrder            升序还是降序，为空则降序
     * @param clazz                返回的类型
     * @return List
     */
    public <T> List<T> query(String indexName, String indexType, List<EsDataQueryDto> esDataQueryDtoList, EsGeoPointSortDto geoPointDtoSortParam, SortOrder sortOrder, Class<T> clazz) throws IOException {
        if (!esSwitch) {
            return new ArrayList<>();
        }
        return query(indexName, indexType, null, esDataQueryDtoList, null, geoPointDtoSortParam, sortOrder, null, null, clazz);
    }


    /**
     * 查询
     *
     * @param indexName            索引名字
     * @param indexType            索引类型
     * @param esGeoPointDto        经纬度查询参数
     * @param esDataQueryDtoList   参数
     * @param sortParam            普通參數排序 不排序则为空 如果进行了排序，会返回es中的排序字段sort，需要用户在返回的实体类中添加sort字段
     * @param geoPointDtoSortParam 经纬度參數排序 不排序则为空 如果进行了排序，会返回es中的排序字段sort，需要用户在返回的实体类中添加sort字段
     * @param sortOrder            升序还是降序，为空则降序
     * @param pageSize             searchAfterSort搜索的页大小
     * @param searchAfterSort      sort值
     * @param clazz                返回的类型
     * @return List
     */
    public <T> List<T> query(String indexName, String indexType, EsGeoPointDto esGeoPointDto, List<EsDataQueryDto> esDataQueryDtoList, String sortParam, EsGeoPointSortDto geoPointDtoSortParam, SortOrder sortOrder, Integer pageSize, Object[] searchAfterSort, Class<T> clazz) throws IOException {
        List<T> list = new ArrayList<>();
        if (!esSwitch) {
            return list;
        }
        SearchSourceBuilder sourceBuilder = getSearchSourceBuilder(esGeoPointDto, esDataQueryDtoList, sortParam, geoPointDtoSortParam, sortOrder);
        executeQuery(indexName, indexType, list, null, clazz, sourceBuilder, null);
        return list;
    }


    /**
     * 查询(分页)
     *
     * @param indexName          索引名字
     * @param indexType          索引类型
     * @param esDataQueryDtoList 参数
     * @param pageNo             页码
     * @param pageSize           页大小
     * @param clazz              返回的类型
     * @return PageInfo
     */
    public <T> PageInfo<T> queryPage(String indexName, String indexType, List<EsDataQueryDto> esDataQueryDtoList, Integer pageNo, Integer pageSize, Class<T> clazz) throws IOException {
        return queryPage(indexName, indexType, esDataQueryDtoList, null, null, pageNo, pageSize, clazz);
    }

    /**
     * 查询(分页)
     *
     * @param indexName          索引名字
     * @param indexType          索引类型
     * @param esDataQueryDtoList 参数
     * @param sortParam          排序参数 不排序则为空 如果进行了排序，会返回es中的排序字段sort，需要用户在返回的实体类中添加sort字段
     * @param sortOrder          升序还是降序，为空则降序
     * @param pageNo             页码
     * @param pageSize           页大小
     * @param clazz              返回的类型
     * @return PageInfo
     */
    public <T> PageInfo<T> queryPage(String indexName, String indexType, List<EsDataQueryDto> esDataQueryDtoList, String sortParam, SortOrder sortOrder, Integer pageNo, Integer pageSize, Class<T> clazz) throws IOException {
        return queryPage(indexName, indexType, null, esDataQueryDtoList, sortParam, null, sortOrder, pageNo, pageSize, clazz);
    }

    /**
     * 查询(分页)
     *
     * @param indexName          索引名字
     * @param indexType          索引类型
     * @param esGeoPointDto      经纬度查询参数
     * @param esDataQueryDtoList 参数
     * @param pageNo             页码
     * @param pageSize           页大小
     * @param clazz              返回的类型
     * @return PageInfo
     */
    public <T> PageInfo<T> queryPage(String indexName, String indexType, EsGeoPointDto esGeoPointDto, List<EsDataQueryDto> esDataQueryDtoList, Integer pageNo, Integer pageSize, Class<T> clazz) throws IOException {
        return queryPage(indexName, indexType, esGeoPointDto, esDataQueryDtoList, null, null, null, pageNo, pageSize, clazz);
    }

    /**
     * 查询(分页)
     *
     * @param indexName          索引名字
     * @param indexType          索引类型
     * @param esGeoPointDto      经纬度查询参数
     * @param esDataQueryDtoList 参数
     * @param sortParam          排序参数 不排序则为空 如果进行了排序，会返回es中的排序字段sort，需要用户在返回的实体类中添加sort字段
     * @param sortOrder          升序还是降序，为空则降序
     * @param pageNo             页码
     * @param pageSize           页大小
     * @param clazz              返回的类型
     * @return
     * @throws IOException
     */
    public <T> PageInfo<T> queryPage(String indexName, String indexType, EsGeoPointDto esGeoPointDto,
                                     List<EsDataQueryDto> esDataQueryDtoList, String sortParam,
                                     EsGeoPointSortDto geoPointDtoSortParam, SortOrder sortOrder, Integer pageNo,
                                     Integer pageSize, Class<T> clazz) throws IOException {
        // 初始化列表和分页信息
        List<T> list = new ArrayList<>();
        PageInfo<T> pageInfo = new PageInfo<>(list);
        pageInfo.setPageNum(pageNo);
        pageInfo.setPageSize(pageSize);

        // 如果ES开关关闭，则直接返回空的分页信息
        if (!esSwitch) {
            return pageInfo;
        }

        // 构建查询源，包括经纬度查询参数、普通查询参数、排序参数等
        SearchSourceBuilder sourceBuilder = getSearchSourceBuilder(esGeoPointDto, esDataQueryDtoList, sortParam, geoPointDtoSortParam, sortOrder);

        // 设置分页参数
        sourceBuilder.from((pageNo - 1) * pageSize);
        sourceBuilder.size(pageSize);

        // 执行查询
        executeQuery(indexName, indexType, list, pageInfo, clazz, sourceBuilder, null);

        return pageInfo;
    }

    /**
     * 构建搜索源构建器
     *
     * @param esGeoPointDto        地理位置查询参数对象，用于构建地理位置查询
     * @param esDataQueryDtoList   数据查询参数列表，用于构建布尔查询
     * @param sortParam            排序参数名称，用于指定排序字段
     * @param geoPointDtoSortParam 地理位置排序参数对象，用于构建地理位置排序
     * @param sortOrder            排序顺序，默认为降序
     * @return 返回一个配置好的SearchSourceBuilder对象
     */
    private SearchSourceBuilder getSearchSourceBuilder(EsGeoPointDto esGeoPointDto, List<EsDataQueryDto> esDataQueryDtoList, String sortParam, EsGeoPointSortDto geoPointDtoSortParam, SortOrder sortOrder) {
        // 初始化搜索源构建器
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        // 如果排序顺序未提供，则默认为降序
        if (Objects.isNull(sortOrder)) {
            sortOrder = SortOrder.DESC;
        }

        // 如果排序参数不为空，则构建字段排序
        if (StringUtil.isNotEmpty(sortParam)) {
            FieldSortBuilder sort = SortBuilders.fieldSort(sortParam);
            sort.order(sortOrder);
            sourceBuilder.sort(sort);
        }

        // 如果地理位置排序参数不为空，则构建地理位置排序
        if (Objects.nonNull(geoPointDtoSortParam)) {
            GeoDistanceSortBuilder sort = SortBuilders.geoDistanceSort("geoPoint", geoPointDtoSortParam.getLatitude().doubleValue(), geoPointDtoSortParam.getLongitude().doubleValue());
            sort.unit(DistanceUnit.METERS);
            sort.order(sortOrder);
            sourceBuilder.sort(sort);
        }

        // 如果地理位置查询参数不为空，则构建地理位置查询
        if (Objects.nonNull(esGeoPointDto)) {
            QueryBuilder geoQuery = new GeoDistanceQueryBuilder(esGeoPointDto.getParamName()).distance(Long.MAX_VALUE, DistanceUnit.KILOMETERS)
                    .point(esGeoPointDto.getLatitude().doubleValue(), esGeoPointDto.getLongitude().doubleValue()).geoDistance(GeoDistance.PLANE);
            sourceBuilder.query(geoQuery);
        }

        // 初始化布尔查询构建器
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

        // 遍历数据查询参数列表，构建布尔查询
        for (EsDataQueryDto esDataQueryDto : esDataQueryDtoList) {
            String paramName = esDataQueryDto.getParamName();
            Object paramValue = esDataQueryDto.getParamValue();
            Date startTime = esDataQueryDto.getStartTime();
            Date endTime = esDataQueryDto.getEndTime();
            boolean analyse = esDataQueryDto.isAnalyse();

            // 如果参数值不为空，则根据参数类型构建相应的查询
            if (Objects.nonNull(paramValue)) {
                if (paramValue instanceof Collection) {
                    if (analyse) {
                        BoolQueryBuilder builds = QueryBuilders.boolQuery();
                        Collection<?> collection = (Collection<?>) paramValue;
                        for (Object value : collection) {
                            builds.should(QueryBuilders.matchQuery(paramName, value));
                        }
                        boolQuery.must(builds);
                    }
                    else {
                        QueryBuilder builds = QueryBuilders.termsQuery(paramName, (Collection<?>) paramValue);
                        boolQuery.must(builds);
                    }
                }
                else {
                    QueryBuilder builds;
                    if (analyse) {
                        builds = QueryBuilders.matchQuery(paramName, paramValue);
                    }
                    else {
                        builds = QueryBuilders.termQuery(paramName, paramValue);
                    }
                    boolQuery.must(builds);
                }
            }

            // 如果时间范围不为空，则构建范围查询
            if (Objects.nonNull(startTime) || Objects.nonNull(endTime)) {
                QueryBuilder builds = QueryBuilders.rangeQuery(paramName)
                        .from(startTime).to(endTime).includeLower(true);
                boolQuery.must(builds);
            }
        }

        // 启用总命中数跟踪，并设置查询为布尔查询
        sourceBuilder.trackTotalHits(true);
        sourceBuilder.query(boolQuery);

        // 返回配置好的搜索源构建器
        return sourceBuilder;
    }

    /**
     * 执行ES查询方法
     * 该方法用于构建ES查询请求，发送请求，并处理响应结果
     * 它会根据提供的索引名称、类型、查询构建器等参数来执行查询，并将结果解析到指定的列表中
     *
     * @param indexName              索引名称，用于指定查询的ES索引
     * @param indexType              索引类型，用于兼容不同版本的ES（某些版本需要指定类型）
     * @param list                   用于存储查询结果的列表，类型为泛型T
     * @param pageInfo               分页信息对象，用于接收查询结果的总记录数
     * @param clazz                  结果对象的类类型，用于将解析的JSON转换为指定的对象类型
     * @param sourceBuilder          查询构建器，包含查询的具体条件和参数
     * @param highLightFieldNameList 需要高亮的字段名称列表，用于在结果中标识高亮部分
     * @throws IOException 当执行查询过程中发生I/O错误时抛出
     */
    public <T> void executeQuery(String indexName, String indexType, List<T> list, PageInfo<T> pageInfo, Class<T> clazz,
                                 SearchSourceBuilder sourceBuilder, List<String> highLightFieldNameList) throws IOException {
        // 将查询构建器转换为字符串形式
        String string = sourceBuilder.toString();

        // 创建HTTP实体，包含查询字符串，设置内容类型为JSON
        HttpEntity entity = new NStringEntity(string, ContentType.APPLICATION_JSON);

        // 构建请求的终点URL，根据esTypeSwitch决定是否包含类型
        StringBuilder endpointStringBuilder = new StringBuilder("/" + indexName);
        if (esTypeSwitch) {
            endpointStringBuilder.append("/").append(indexType).append("/_search");
        }
        else {
            endpointStringBuilder.append("/_search");
        }
        String endpoint = endpointStringBuilder.toString();

        // 日志记录查询DSL字符串
        log.info("query execute query dsl : {}", string);

        // 创建请求对象，设置请求方法为POST，终点为构建的URL
        Request request = new Request("POST", endpoint);
        request.setEntity(entity);
        request.addParameters(Collections.emptyMap());

        // 执行请求，获取响应
        Response response = restClient.performRequest(request);

        // 从响应中提取结果字符串
        String result = EntityUtils.toString(response.getEntity());

        // 如果结果为空，则直接返回
        if (StringUtil.isEmpty(result)) {
            return;
        }

        // 解析结果为JSON对象
        JSONObject resultJsonObject = JSONObject.parseObject(result);

        // 如果解析结果为空，则直接返回
        if (Objects.isNull(resultJsonObject)) {
            return;
        }

        // 获取查询结果中的hits部分
        JSONObject hits = resultJsonObject.getJSONObject("hits");
        if (Objects.isNull(hits)) {
            return;
        }

        // 根据esTypeSwitch获取总记录数
        Long value = null;
        if (esTypeSwitch) {
            value = hits.getLong("total");
        }
        else {
            JSONObject totalJsonObject = hits.getJSONObject("total");
            if (Objects.nonNull(totalJsonObject)) {
                value = totalJsonObject.getLong("value");
            }
        }

        // 如果提供了分页信息对象且总记录数不为空，则设置总记录数
        if (Objects.nonNull(pageInfo) && Objects.nonNull(value)) {
            pageInfo.setTotal(value);
        }

        // 获取查询结果中的数据数组
        JSONArray arrayData = hits.getJSONArray("hits");
        if (Objects.isNull(arrayData) || arrayData.isEmpty()) {
            return;
        }

        // 遍历数据数组，解析每个结果项
        for (int i = 0, size = arrayData.size(); i < size; i++) {
            JSONObject data = arrayData.getJSONObject(i);

            // 如果解析结果为空，则跳过当前循环
            if (Objects.isNull(data)) {
                continue;
            }

            // 获取结果项的ID和源数据
            String esId = data.getString("_id");
            JSONObject jsonObject = data.getJSONObject("_source");

            // 处理排序字段
            JSONArray jsonArray = data.getJSONArray("sort");

            // 如果排序字段不为null且不为空
            if (Objects.nonNull(jsonArray) && !jsonArray.isEmpty()) {
                // 获取数组中的第一个元素作为排序参数
                Long sort = jsonArray.getLong(0);
                // 将排序参数放入jsonObject中
                jsonObject.put("sort", sort);
            }

            // 处理高亮字段
            JSONObject highlight = data.getJSONObject("highlight");

            // 如果高亮字段不为null且需要高亮的字段列表不为null
            if (Objects.nonNull(highlight) && Objects.nonNull(highLightFieldNameList)) {
                // 对于每个需要高亮的字段名称
                for (String highLightFieldName : highLightFieldNameList) {
                    // 获取高亮字段的值数组
                    JSONArray highLightFieldValue = highlight.getJSONArray(highLightFieldName);
                    // 如果高亮字段的值数组为空或无元素，则跳过当前字段
                    if (Objects.isNull(highLightFieldValue) || highLightFieldValue.isEmpty()) {
                        continue;
                    }
                    // 将高亮字段的第一个值放入JSON对象中
                    jsonObject.put(highLightFieldName, highLightFieldValue.get(0));
                }
            }

            // 如果ID不为空，则添加到结果对象中
            if (StringUtil.isNotEmpty(esId)) {
                jsonObject.put("esId", esId);
            }

            // 将解析的JSON对象转换为指定的类类型，并添加到结果列表中
            list.add(JSONObject.parseObject(jsonObject.toJSONString(), clazz));
        }
    }

    /**
     * 根据文档ID删除指定索引中的文档
     * 此方法在执行前会检查esSwitch标志，如果不为true，则直接返回，不执行删除操作
     * 它通过发送DELETE请求到Elasticsearch的相应索引和文档ID来实现文档的删除
     *
     * @param index      索引名称，表示要在哪个索引中删除文档
     * @param documentId 文档ID，指定要删除的文档
     */
    public void deleteByDocumentId(String index, String documentId) {
        // 检查ES开关状态，如果未开启，则不执行任何操作
        if (!esSwitch) {
            return;
        }
        try {
            // 创建一个DELETE请求，指向特定索引和文档ID
            Request request = new Request("DELETE", "/" + index + "/_doc/" + documentId);
            // 添加空参数，表示不向请求中添加任何额外参数
            request.addParameters(Collections.<String, String>emptyMap());
            // 执行请求并获取响应
            Response response = restClient.performRequest(request);
            // 记录删除操作的结果信息
            log.info("deleteByDocumentId result : {}", response.getStatusLine().getReasonPhrase());
        }
        catch (Exception e) {
            // 如果在执行请求过程中发生异常，记录错误信息
            log.error("deleteData error", e);
        }
    }
}
