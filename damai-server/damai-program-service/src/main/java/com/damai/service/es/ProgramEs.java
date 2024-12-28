package com.damai.service.es;

import cn.hutool.core.collection.CollectionUtil;
import com.damai.core.SpringUtil;
import com.damai.dto.*;
import com.damai.enums.BusinessStatus;
import com.damai.page.PageUtil;
import com.damai.page.PageVo;
import com.damai.service.init.ProgramDocumentParamName;
import com.damai.service.tool.ProgramPageOrder;
import com.damai.util.BusinessEsHandle;
import com.damai.util.StringUtil;
import com.damai.vo.ProgramHomeVo;
import com.damai.vo.ProgramListVo;
import com.github.pagehelper.PageInfo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.ScriptSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 *
 */
@Slf4j
@Component
public class ProgramEs {

	@Resource
	private BusinessEsHandle businessEsHandle;

	/**
	 * 根据给定的 `programPageListDto` 查询主页节目列表。
	 * 该方法首先通过父节目分类ID集合从 Elasticsearch 中查询每个父节目的相关数据，并根据地区ID或是否为主要节目来动态调整查询条件。
	 * 最终，方法会返回一个包含 `ProgramHomeVo` 对象的列表，每个 `ProgramHomeVo` 对象代表一个父节目的相关信息和节目列表。
	 *
	 * @param programPageListDto 包含查询条件的 DTO，包含父节目分类ID集合、地区ID等信息。
	 * @return 返回一个包含 `ProgramHomeVo` 对象的列表，每个对象包含一个父节目的相关信息和其下的节目列表。
	 * 如果查询过程中发生异常，则返回空列表。
	 * @throws Exception 如果查询过程中发生任何异常（例如 Elasticsearch 查询失败），方法会捕获并记录该异常，但不会抛出。
	 */
	public List<ProgramHomeVo> selectHomeList(ProgramListDto programPageListDto) {
		// 返回的对象
		List<ProgramHomeVo> programHomeVoList = new ArrayList<>();
		try {
			// 按照父节目id集合来进行循环，从ES中查询，主页页面是4个父节目，所以循环4次
			List<Long> parentProgramCategoryIds = programPageListDto.getParentProgramCategoryIds();
			// 对于每一个父节目ID
			for (Long parentProgramCategoryId : parentProgramCategoryIds) {

				List<EsDataQueryDto> esDataQueryDtoList = new ArrayList<>();

				if (Objects.nonNull(programPageListDto.getAreaId())) {
					// 若地区ID不为null
					EsDataQueryDto areaIdQueryDto = new EsDataQueryDto();
					areaIdQueryDto.setParamName(ProgramDocumentParamName.AREA_ID);
					areaIdQueryDto.setParamValue(programPageListDto.getAreaId());
					esDataQueryDtoList.add(areaIdQueryDto);
				}
				else {
					// 若地区ID为null，则需要查询同一个节目分组内的主要节目
					EsDataQueryDto primeQueryDto = new EsDataQueryDto();
					primeQueryDto.setParamName(ProgramDocumentParamName.PRIME);
					primeQueryDto.setParamValue(BusinessStatus.YES.getCode());
					esDataQueryDtoList.add(primeQueryDto);
				}
				// 父节目类型ID集合
				EsDataQueryDto parentProgramCategoryIdQueryDto = new EsDataQueryDto();
				parentProgramCategoryIdQueryDto.setParamName(ProgramDocumentParamName.PARENT_PROGRAM_CATEGORY_ID);
				parentProgramCategoryIdQueryDto.setParamValue(parentProgramCategoryId);
				esDataQueryDtoList.add(parentProgramCategoryIdQueryDto);
				// 从elasticsearch查询引擎中查询前7条数据
				String indexName = SpringUtil.getPrefixDistinctionName() + "-" + ProgramDocumentParamName.INDEX_NAME;
				PageInfo<ProgramListVo> pageInfo = businessEsHandle.queryPage(
						indexName,
						ProgramDocumentParamName.INDEX_TYPE,
						esDataQueryDtoList,
						1,
						7,
						ProgramListVo.class);
				// 如果有结果，封装为ProgramHomeVo对象加入到返回列表中
				if (!pageInfo.getList().isEmpty()) {
					ProgramHomeVo programHomeVo = new ProgramHomeVo();
					programHomeVo.setCategoryName(pageInfo.getList().get(0).getParentProgramCategoryName());
					programHomeVo.setCategoryId(pageInfo.getList().get(0).getParentProgramCategoryId());
					programHomeVo.setProgramListVoList(pageInfo.getList());
					programHomeVoList.add(programHomeVo);
				}
			}
		}
		catch (Exception e) {
			log.error("businessEsHandle.queryPage error", e);
		}

		// 返回列表
		return programHomeVoList;
	}


	public List<ProgramListVo> recommendList(ProgramRecommendListDto programRecommendListDto) {
		List<ProgramListVo> programListVoList = new ArrayList<>();
		try {
			boolean allQueryFlag = true;
			MatchAllQueryBuilder matchAllQueryBuilder = QueryBuilders.matchAllQuery();
			BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
			if (Objects.nonNull(programRecommendListDto.getAreaId())) {
				allQueryFlag = false;
				QueryBuilder builds = QueryBuilders.termQuery(ProgramDocumentParamName.AREA_ID,
						programRecommendListDto.getAreaId());
				boolQuery.must(builds);
			}
			if (Objects.nonNull(programRecommendListDto.getParentProgramCategoryId())) {
				allQueryFlag = false;
				QueryBuilder builds = QueryBuilders.termQuery(ProgramDocumentParamName.PARENT_PROGRAM_CATEGORY_ID,
						programRecommendListDto.getParentProgramCategoryId());
				boolQuery.must(builds);
			}
			if (Objects.nonNull(programRecommendListDto.getProgramId())) {
				allQueryFlag = false;
				QueryBuilder builds = QueryBuilders.termQuery(ProgramDocumentParamName.ID,
						programRecommendListDto.getProgramId());
				boolQuery.mustNot(builds);
			}
			SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
			searchSourceBuilder.query(allQueryFlag ? matchAllQueryBuilder : boolQuery);
			searchSourceBuilder.trackTotalHits(true);
			searchSourceBuilder.from(1);
			searchSourceBuilder.size(10);

			Script script = new Script("Math.random()");
			ScriptSortBuilder scriptSortBuilder = new ScriptSortBuilder(script, ScriptSortBuilder.ScriptSortType.NUMBER);
			scriptSortBuilder.order(SortOrder.ASC);

			searchSourceBuilder.sort(scriptSortBuilder);

			businessEsHandle.executeQuery(
					SpringUtil.getPrefixDistinctionName() + "-" + ProgramDocumentParamName.INDEX_NAME,
					ProgramDocumentParamName.INDEX_TYPE, programListVoList, null, ProgramListVo.class,
					searchSourceBuilder, null);
		}
		catch (Exception e) {
			log.error("recommendList error", e);
		}
		return programListVoList;
	}


	public PageVo<ProgramListVo> selectPage(ProgramPageListDto programPageListDto) {
		PageVo<ProgramListVo> pageVo = new PageVo<>();
		try {
			List<EsDataQueryDto> esDataQueryDtoList = new ArrayList<>();
			if (Objects.nonNull(programPageListDto.getAreaId())) {
				EsDataQueryDto areaIdQueryDto = new EsDataQueryDto();
				areaIdQueryDto.setParamName(ProgramDocumentParamName.AREA_ID);
				areaIdQueryDto.setParamValue(programPageListDto.getAreaId());
				esDataQueryDtoList.add(areaIdQueryDto);
			}
			else {
				EsDataQueryDto primeQueryDto = new EsDataQueryDto();
				primeQueryDto.setParamName(ProgramDocumentParamName.PRIME);
				primeQueryDto.setParamValue(BusinessStatus.YES.getCode());
				esDataQueryDtoList.add(primeQueryDto);
			}
			if (Objects.nonNull(programPageListDto.getParentProgramCategoryId())) {
				EsDataQueryDto parentProgramCategoryIdQueryDto = new EsDataQueryDto();
				parentProgramCategoryIdQueryDto.setParamName(ProgramDocumentParamName.PARENT_PROGRAM_CATEGORY_ID);
				parentProgramCategoryIdQueryDto.setParamValue(programPageListDto.getParentProgramCategoryId());
				esDataQueryDtoList.add(parentProgramCategoryIdQueryDto);
			}
			if (Objects.nonNull(programPageListDto.getProgramCategoryId())) {
				EsDataQueryDto programCategoryIdQueryDto = new EsDataQueryDto();
				programCategoryIdQueryDto.setParamName(ProgramDocumentParamName.PROGRAM_CATEGORY_ID);
				programCategoryIdQueryDto.setParamValue(programPageListDto.getProgramCategoryId());
				esDataQueryDtoList.add(programCategoryIdQueryDto);
			}
			if (Objects.nonNull(programPageListDto.getStartDateTime()) &&
					Objects.nonNull(programPageListDto.getEndDateTime())) {
				EsDataQueryDto showDayTimeQueryDto = new EsDataQueryDto();
				showDayTimeQueryDto.setParamName(ProgramDocumentParamName.SHOW_DAY_TIME);
				showDayTimeQueryDto.setStartTime(programPageListDto.getStartDateTime());
				showDayTimeQueryDto.setEndTime(programPageListDto.getEndDateTime());
				esDataQueryDtoList.add(showDayTimeQueryDto);
			}

			ProgramPageOrder programPageOrder = getProgramPageOrder(programPageListDto);

			PageInfo<ProgramListVo> programListVoPageInfo = businessEsHandle.queryPage(
					SpringUtil.getPrefixDistinctionName() + "-" + ProgramDocumentParamName.INDEX_NAME,
					ProgramDocumentParamName.INDEX_TYPE,
					esDataQueryDtoList,
					programPageOrder.sortParam,
					programPageOrder.sortOrder,
					programPageListDto.getPageNumber(),
					programPageListDto.getPageSize(),
					ProgramListVo.class);
			pageVo = PageUtil.convertPage(programListVoPageInfo, programListVo -> programListVo);
		}
		catch (Exception e) {
			log.error("selectPage error", e);
		}
		return pageVo;
	}

	public ProgramPageOrder getProgramPageOrder(ProgramPageListDto programPageListDto) {
		ProgramPageOrder programPageOrder = new ProgramPageOrder();
		switch (programPageListDto.getType()) {
			//推荐排序
			case 2:
				programPageOrder.sortParam = ProgramDocumentParamName.HIGH_HEAT;
				programPageOrder.sortOrder = SortOrder.DESC;
				break;
			//最近开场
			case 3:
				programPageOrder.sortParam = ProgramDocumentParamName.SHOW_TIME;
				programPageOrder.sortOrder = SortOrder.ASC;
				break;
			//最新上架
			case 4:
				programPageOrder.sortParam = ProgramDocumentParamName.ISSUE_TIME;
				programPageOrder.sortOrder = SortOrder.ASC;
				break;
			//相关度排序
			default:
				programPageOrder.sortParam = null;
				programPageOrder.sortOrder = null;
		}
		return programPageOrder;
	}

	/**
	 * 根据搜索条件查询节目列表
	 *
	 * @param programSearchDto 节目搜索DTO，包含各种搜索条件
	 * @return 包含节目列表的分页对象
	 */
	public PageVo<ProgramListVo> search(ProgramSearchDto programSearchDto) {
		// 待返回的查询结果
		PageVo<ProgramListVo> pageVo = new PageVo<>();
		try {
			// 构建布尔查询
			BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
			// 如果地区ID不为空，则添加地区ID的条件到布尔查询中
			if (Objects.nonNull(programSearchDto.getAreaId())) {
				QueryBuilder builds = QueryBuilders.termQuery(ProgramDocumentParamName.AREA_ID, programSearchDto.getAreaId());
				boolQuery.must(builds);
			}
			// 如果父节目类别ID不为空，则添加到布尔查询中
			if (Objects.nonNull(programSearchDto.getParentProgramCategoryId())) {
				QueryBuilder builds = QueryBuilders.termQuery(ProgramDocumentParamName.PARENT_PROGRAM_CATEGORY_ID, programSearchDto.getParentProgramCategoryId());
				boolQuery.must(builds);
			}
			// 如果开始时间和结束时间都不为空，则添加时间范围查询到布尔查询中
			if (Objects.nonNull(programSearchDto.getStartDateTime()) &&
					Objects.nonNull(programSearchDto.getEndDateTime())) {
				QueryBuilder builds = QueryBuilders.rangeQuery(ProgramDocumentParamName.SHOW_DAY_TIME)
						.from(programSearchDto.getStartDateTime()).to(programSearchDto.getEndDateTime()).includeLower(true);
				boolQuery.must(builds);
			}
			// 如果搜索内容不为空，则构建内部布尔查询以匹配标题或演员
			if (StringUtil.isNotEmpty(programSearchDto.getContent())) {
				BoolQueryBuilder innerBoolQuery = QueryBuilders.boolQuery();
				innerBoolQuery.should(QueryBuilders.matchQuery(ProgramDocumentParamName.TITLE, programSearchDto.getContent()));
				innerBoolQuery.should(QueryBuilders.matchQuery(ProgramDocumentParamName.ACTOR, programSearchDto.getContent()));
				innerBoolQuery.minimumShouldMatch(1);
				boolQuery.must(innerBoolQuery);
			}

			// 创建搜索源构建器
			SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
			// 获取节目分页排序条件
			ProgramPageOrder programPageOrder = getProgramPageOrder(programSearchDto);
			// 如果排序参数和排序顺序都不为空，则添加排序条件到搜索源构建器中
			if (Objects.nonNull(programPageOrder.sortParam) && Objects.nonNull(programPageOrder.sortOrder)) {
				FieldSortBuilder sort = SortBuilders.fieldSort(programPageOrder.sortParam);
				sort.order(programPageOrder.sortOrder);
				searchSourceBuilder.sort(sort);
			}
			// 设置查询条件、高亮等
			searchSourceBuilder.query(boolQuery);
			searchSourceBuilder.trackTotalHits(true);
			searchSourceBuilder.from((programSearchDto.getPageNumber() - 1) * programSearchDto.getPageSize());
			searchSourceBuilder.size(programSearchDto.getPageSize());
			searchSourceBuilder.highlighter(getHighlightBuilder(Arrays.asList(ProgramDocumentParamName.TITLE,
					ProgramDocumentParamName.ACTOR)));

			// 执行查询并处理结果
			List<ProgramListVo> list = new ArrayList<>();
			PageInfo<ProgramListVo> pageInfo = new PageInfo<>(list);
			pageInfo.setPageNum(programSearchDto.getPageNumber());
			pageInfo.setPageSize(programSearchDto.getPageSize());
			businessEsHandle.executeQuery(SpringUtil.getPrefixDistinctionName() + "-" + ProgramDocumentParamName.INDEX_NAME,
					ProgramDocumentParamName.INDEX_TYPE, list, pageInfo, ProgramListVo.class,
					searchSourceBuilder, Arrays.asList(ProgramDocumentParamName.TITLE, ProgramDocumentParamName.ACTOR));

			// 将查询结果转换为待返回的分页对象
			pageVo = PageUtil.convertPage(pageInfo, programListVo -> programListVo);
		}
		catch (Exception e) {
			// 记录查询错误日志
			log.error("search error", e);
		}
		return pageVo;
	}

	/**
	 * 创建并返回一个配置了高亮设置的HighlightBuilder对象
	 * 该方法根据提供的字段名称列表，为每个字段配置高亮显示的参数
	 *
	 * @param fieldNameList 字段名称的列表，用于指定需要高亮显示的字段
	 * @return HighlightBuilder对象，配置了指定字段的高亮设置
	 */
	public HighlightBuilder getHighlightBuilder(List<String> fieldNameList) {
		// 创建一个HighlightBuilder
		HighlightBuilder highlightBuilder = new HighlightBuilder();
		// 遍历字段名称列表，为每个字段配置高亮设置
		for (String fieldName : fieldNameList) {
			// 为特定字段添加高亮设置
			HighlightBuilder.Field highlightTitle = new HighlightBuilder.Field(fieldName);
			// 设置高亮显示前缀
			highlightTitle.preTags("<em>");
			// 设置高亮显示后缀
			highlightTitle.postTags("</em>");
			// 将配置好的高亮字段添加到HighlightBuilder中
			highlightBuilder.field(highlightTitle);
		}
		// 返回配置了高亮设置的HighlightBuilder对象
		return highlightBuilder;
	}

	public void deleteByProgramId(Long programId) {
		try {
			List<EsDataQueryDto> esDataQueryDtoList = new ArrayList<>();
			EsDataQueryDto programIdDto = new EsDataQueryDto();
			programIdDto.setParamName(ProgramDocumentParamName.ID);
			programIdDto.setParamValue(programId);
			esDataQueryDtoList.add(programIdDto);

			List<ProgramListVo> programListVos =
					businessEsHandle.query(
							SpringUtil.getPrefixDistinctionName() + "-" + ProgramDocumentParamName.INDEX_NAME,
							ProgramDocumentParamName.INDEX_TYPE,
							esDataQueryDtoList,
							ProgramListVo.class);
			if (CollectionUtil.isNotEmpty(programListVos)) {
				for (ProgramListVo programListVo : programListVos) {
					businessEsHandle.deleteByDocumentId(
							SpringUtil.getPrefixDistinctionName() + "-" + ProgramDocumentParamName.INDEX_NAME,
							programListVo.getEsId());
				}
			}
		}
		catch (Exception e) {
			log.error("deleteByProgramId error", e);
		}
	}
}
