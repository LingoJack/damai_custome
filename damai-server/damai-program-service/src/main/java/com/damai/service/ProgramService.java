package com.damai.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.damai.BusinessThreadPool;
import com.damai.RedisStreamPushHandler;
import com.damai.client.BaseDataClient;
import com.damai.client.OrderClient;
import com.damai.client.UserClient;
import com.damai.common.ApiResponse;
import com.damai.core.RedisKeyManage;
import com.damai.dto.AccountOrderCountDto;
import com.damai.dto.AreaGetDto;
import com.damai.dto.AreaSelectDto;
import com.damai.dto.ProgramAddDto;
import com.damai.dto.ProgramGetDto;
import com.damai.dto.ProgramInvalidDto;
import com.damai.dto.ProgramListDto;
import com.damai.dto.ProgramOperateDataDto;
import com.damai.dto.ProgramPageListDto;
import com.damai.dto.ProgramRecommendListDto;
import com.damai.dto.ProgramResetExecuteDto;
import com.damai.dto.ProgramSearchDto;
import com.damai.dto.TicketCategoryCountDto;
import com.damai.dto.TicketUserListDto;
import com.damai.entity.Program;
import com.damai.entity.ProgramCategory;
import com.damai.entity.ProgramGroup;
import com.damai.entity.ProgramJoinShowTime;
import com.damai.entity.ProgramShowTime;
import com.damai.entity.Seat;
import com.damai.entity.TicketCategory;
import com.damai.entity.TicketCategoryAggregate;
import com.damai.enums.BaseCode;
import com.damai.enums.BusinessStatus;
import com.damai.enums.CompositeCheckType;
import com.damai.enums.SellStatus;
import com.damai.exception.DaMaiFrameException;
import com.damai.initialize.impl.composite.CompositeContainer;
import com.damai.mapper.ProgramCategoryMapper;
import com.damai.mapper.ProgramGroupMapper;
import com.damai.mapper.ProgramMapper;
import com.damai.mapper.ProgramShowTimeMapper;
import com.damai.mapper.SeatMapper;
import com.damai.mapper.TicketCategoryMapper;
import com.damai.page.PageUtil;
import com.damai.page.PageVo;
import com.damai.redis.RedisCache;
import com.damai.redis.RedisKeyBuild;
import com.damai.repeatexecutelimit.annotion.RepeatExecuteLimit;
import com.damai.service.cache.local.LocalCacheProgram;
import com.damai.service.cache.local.LocalCacheProgramCategory;
import com.damai.service.cache.local.LocalCacheProgramGroup;
import com.damai.service.cache.local.LocalCacheProgramShowTime;
import com.damai.service.cache.local.LocalCacheTicketCategory;
import com.damai.service.constant.ProgramTimeType;
import com.damai.service.es.ProgramEs;
import com.damai.service.lua.ProgramDelCacheData;
import com.damai.service.tool.TokenExpireManager;
import com.damai.servicelock.LockType;
import com.damai.servicelock.annotion.ServiceLock;
import com.damai.threadlocal.BaseParameterHolder;
import com.damai.util.DateUtils;
import com.damai.util.ServiceLockTool;
import com.damai.util.StringUtil;
import com.damai.vo.AccountOrderCountVo;
import com.damai.vo.AreaVo;
import com.damai.vo.ProgramGroupVo;
import com.damai.vo.ProgramHomeVo;
import com.damai.vo.ProgramListVo;
import com.damai.vo.ProgramSimpleInfoVo;
import com.damai.vo.ProgramVo;
import com.damai.vo.TicketCategoryVo;
import com.damai.vo.TicketUserVo;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.damai.constant.Constant.CODE;
import static com.damai.constant.Constant.USER_ID;
import static com.damai.core.DistributedLockConstants.GET_PROGRAM_LOCK;
import static com.damai.core.DistributedLockConstants.PROGRAM_GROUP_LOCK;
import static com.damai.core.DistributedLockConstants.PROGRAM_LOCK;
import static com.damai.core.RepeatExecuteLimitConstants.CANCEL_PROGRAM_ORDER;
import static com.damai.util.DateUtils.FORMAT_DATE;

/**
 * 节目Service
 **/
@Slf4j
@Service
public class ProgramService extends ServiceImpl<ProgramMapper, Program> {

	@Autowired
	private UidGenerator uidGenerator;

	@Autowired
	private ProgramMapper programMapper;

	@Autowired
	private ProgramGroupMapper programGroupMapper;

	@Autowired
	private ProgramShowTimeMapper programShowTimeMapper;

	@Autowired
	private ProgramCategoryMapper programCategoryMapper;

	@Autowired
	private TicketCategoryMapper ticketCategoryMapper;

	@Autowired
	private SeatMapper seatMapper;

	@Autowired
	private BaseDataClient baseDataClient;

	@Autowired
	private UserClient userClient;

	@Autowired
	private OrderClient orderClient;

	@Autowired
	private RedisCache redisCache;

	@Lazy
	@Autowired
	private ProgramService programService;

	@Autowired
	private ProgramShowTimeService programShowTimeService;

	@Autowired
	private TicketCategoryService ticketCategoryService;

	@Autowired
	private ProgramCategoryService programCategoryService;

	@Autowired
	private ProgramEs programEs;

	@Autowired
	private ServiceLockTool serviceLockTool;

	@Autowired
	private RedisStreamPushHandler redisStreamPushHandler;

	@Autowired
	private LocalCacheProgram localCacheProgram;

	@Autowired
	private LocalCacheProgramGroup localCacheProgramGroup;

	@Autowired
	private LocalCacheProgramCategory localCacheProgramCategory;

	@Autowired
	private LocalCacheProgramShowTime localCacheProgramShowTime;

	@Autowired
	private LocalCacheTicketCategory localCacheTicketCategory;

	@Autowired
	private CompositeContainer compositeContainer;

	@Autowired
	private TokenExpireManager tokenExpireManager;

	@Autowired
	private ProgramDelCacheData programDelCacheData;

	/**
	 * 添加节目
	 *
	 * @param programAddDto 添加节目数据的入参
	 * @return 添加节目后的id
	 */
	public Long add(ProgramAddDto programAddDto) {
		Program program = new Program();
		BeanUtil.copyProperties(programAddDto, program);
		program.setId(uidGenerator.getUid());
		programMapper.insert(program);
		return program.getId();
	}

	/**
	 * 搜索
	 *
	 * @param programSearchDto 搜索节目数据的入参
	 * @return 执行后的结果
	 */
	public PageVo<ProgramListVo> search(ProgramSearchDto programSearchDto) {
		// 根据预设状态自动填充对应的时间参数
		setQueryTime(programSearchDto);

		// 通过elasticsearch查询
		return programEs.search(programSearchDto);
	}

	/**
	 * 查询主页信息
	 *
	 * @param programListDto 查询节目数据的入参
	 * @return 执行后的结果
	 */
	public List<ProgramHomeVo> selectHomeList(ProgramListDto programListDto) {
		// 先从elasticsearch中查询
		List<ProgramHomeVo> programHomeVoList = programEs.selectHomeList(programListDto);
		if (CollectionUtil.isNotEmpty(programHomeVoList)) {
			// elasticsearch中有查询到，直接返回
			return programHomeVoList;
		}
		// 若es中没有查询到，则从数据库中查询
		return dbSelectHomeList(programListDto);
	}

	/**
	 * 查询主页信息（数据库查询）
	 *
	 * @param programPageListDto 查询节目数据的入参，包含地区ID、父节目ID列表
	 * @return 执行后的结果
	 */
	private List<ProgramHomeVo> dbSelectHomeList(ProgramListDto programPageListDto) {
		// 定义返回的结果
		List<ProgramHomeVo> programHomeVoList = new ArrayList<>();

		// 根据父节目类型id来查询节目类型map，key：节目类型id，value：节目类型名
		Map<Long, String> programCategoryMap = selectProgramCategoryMap(programPageListDto.getParentProgramCategoryIds());

		// 查询节目列表
		List<Program> programList = programMapper.selectHomeList(programPageListDto);

		// 如果节目列表为空，那么直接返回空map
		if (CollectionUtil.isEmpty(programList)) {
			return programHomeVoList;
		}

		// 从节目列表中单独再映射出节目ID表
		List<Long> programIdList = programList.stream().map(Program::getId).collect(Collectors.toList());

		// 根据节目id集合查询节目演出时间集合
		LambdaQueryWrapper<ProgramShowTime> programShowTimeLambdaQueryWrapper = Wrappers.lambdaQuery(ProgramShowTime.class)
				.in(ProgramShowTime::getProgramId, programIdList);
		List<ProgramShowTime> programShowTimeList = programShowTimeMapper.selectList(programShowTimeLambdaQueryWrapper);

		// 按照节目ID将演出时间列表进行分组，获得以节目ID为键，演出时间集合为值的map
		Map<Long, List<ProgramShowTime>> programShowTimeMap = programShowTimeList.stream().collect(Collectors.groupingBy(ProgramShowTime::getProgramId));

		// 获得以节目ID为键，票档统计对象为值的map
		Map<Long, TicketCategoryAggregate> ticketCategorieMap = selectTicketCategorieMap(programIdList);

		// 分为以父节目ID为键，节目集合为值的map
		Map<Long, List<Program>> programMap = programList.stream().collect(Collectors.groupingBy(Program::getParentProgramCategoryId));

		// 对于每个父节目ID-节目集合的项
		for (Entry<Long, List<Program>> programEntry : programMap.entrySet()) {

			// 主页节目VO所需要的节目列表VO列表对象
			List<ProgramListVo> programListVoList = new ArrayList<>();

			// 父节目类型id
			Long key = programEntry.getKey();

			//节目集合
			List<Program> value = programEntry.getValue();

			// 对于当前父ID下的节目集合的每一个节目
			for (Program program : value) {
				// 需要添加到节目列表VO列表的节目列表VO
				ProgramListVo programListVo = new ProgramListVo();
				// 复制基础信息到节目列表VO
				BeanUtil.copyProperties(program, programListVo);
				// 演出时间
				programListVo.setShowTime(
						Optional.ofNullable(programShowTimeMap.get(program.getId()))
								.filter(list -> !list.isEmpty())
								.map(list -> list.get(0))
								.map(ProgramShowTime::getShowTime)
								.orElse(null));
				// 演出时间(精确到天)
				programListVo.setShowDayTime(
						Optional.ofNullable(programShowTimeMap.get(program.getId()))
								.filter(list -> !list.isEmpty())
								.map(list -> list.get(0))
								.map(ProgramShowTime::getShowDayTime)
								.orElse(null));
				// 演出时间所在的星期
				programListVo.setShowWeekTime(
						Optional.ofNullable(programShowTimeMap.get(program.getId()))
								.filter(list -> !list.isEmpty())
								.map(list -> list.get(0))
								.map(ProgramShowTime::getShowWeekTime)
								.orElse(null));
				// 节目最高价
				programListVo.setMaxPrice(
						Optional.ofNullable(ticketCategorieMap.get(program.getId()))
								.map(TicketCategoryAggregate::getMaxPrice).orElse(null));
				// 节目最低价
				programListVo.setMinPrice(
						Optional.ofNullable(ticketCategorieMap.get(program.getId()))
								.map(TicketCategoryAggregate::getMinPrice).orElse(null));
				programListVoList.add(programListVo);
			}
			ProgramHomeVo programHomeVo = new ProgramHomeVo();
			// 节目类型名
			programHomeVo.setCategoryName(programCategoryMap.get(key));
			// 节目类型id
			programHomeVo.setCategoryId(key);
			programHomeVo.setProgramListVoList(programListVoList);
			// 节目列表
			programHomeVoList.add(programHomeVo);
		}
		return programHomeVoList;
	}

	/**
	 * 处理时间范围参数
	 * 比如 一天、一周、一个月等
	 * 转换为开始时间和结束时间
	 * <p>
	 * 但我觉得这个逻辑或许可以直接放到前端
	 * <p>
	 * 根据提供的程序列表DTO中的时间类型，设置相应的开始时间和结束时间
	 * 这有助于在查询时确定节目单的时间范围
	 *
	 * @param programPageListDto 包含时间类型和其他参数的节目单列表DTO对象
	 */
	public void setQueryTime(ProgramPageListDto programPageListDto) {
		// 根据时间类型设置开始时间和结束时间
		switch (programPageListDto.getTimeType()) {
			case ProgramTimeType.TODAY:
				// 当时间类型为今天时，开始时间和结束时间都设置为当前日期
				programPageListDto.setStartDateTime(DateUtils.now(FORMAT_DATE));
				programPageListDto.setEndDateTime(DateUtils.now(FORMAT_DATE));
				break;
			case ProgramTimeType.TOMORROW:
				// 当时间类型为明天时，开始时间设置为当前日期，结束时间设置为后一天
				programPageListDto.setStartDateTime(DateUtils.now(FORMAT_DATE));
				programPageListDto.setEndDateTime(DateUtils.addDay(DateUtils.now(FORMAT_DATE), 1));
				break;
			case ProgramTimeType.WEEK:
				// 当时间类型为一周时，开始时间设置为当前日期，结束时间设置为后一周
				programPageListDto.setStartDateTime(DateUtils.now(FORMAT_DATE));
				programPageListDto.setEndDateTime(DateUtils.addWeek(DateUtils.now(FORMAT_DATE), 1));
				break;
			case ProgramTimeType.MONTH:
				// 当时间类型为一个月时，开始时间设置为当前日期，结束时间设置为后一个月
				programPageListDto.setStartDateTime(DateUtils.now(FORMAT_DATE));
				programPageListDto.setEndDateTime(DateUtils.addMonth(DateUtils.now(FORMAT_DATE), 1));
				break;
			case ProgramTimeType.CALENDAR:
				// 当时间类型为自定义日历时，检查开始时间和结束时间是否存在
				if (Objects.isNull(programPageListDto.getStartDateTime())) {
					throw new DaMaiFrameException(BaseCode.START_DATE_TIME_NOT_EXIST);
				}
				if (Objects.isNull(programPageListDto.getEndDateTime())) {
					throw new DaMaiFrameException(BaseCode.END_DATE_TIME_NOT_EXIST);
				}
				break;
			default:
				// 对于其他未知的时间类型，将开始时间和结束时间设置为null
				programPageListDto.setStartDateTime(null);
				programPageListDto.setEndDateTime(null);
				break;
		}
	}

	/**
	 * 查询分类列表（数据库查询）
	 *
	 * @param programPageListDto 查询节目数据的入参
	 * @return 执行后的结果
	 */
	public PageVo<ProgramListVo> selectPage(ProgramPageListDto programPageListDto) {
		setQueryTime(programPageListDto);
		PageVo<ProgramListVo> pageVo = programEs.selectPage(programPageListDto);
		if (CollectionUtil.isNotEmpty(pageVo.getList())) {
			return pageVo;
		}
		return dbSelectPage(programPageListDto);
	}

	/**
	 * 推荐列表
	 *
	 * @param programRecommendListDto 查询节目数据的入参
	 * @return 执行后的结果
	 */
	public List<ProgramListVo> recommendList(ProgramRecommendListDto programRecommendListDto) {
		compositeContainer.execute(CompositeCheckType.PROGRAM_RECOMMEND_CHECK.getValue(), programRecommendListDto);
		return programEs.recommendList(programRecommendListDto);
	}

	/**
	 * 根据条件从数据库中查询节目列表
	 * 此方法通过连表查询获得节目及其展示时间的信息，同时获取相关节目类型和区域信息
	 *
	 * @param programPageListDto 节目列表查询条件DTO，包含查询所需的各种条件
	 * @return 返回一个包含节目列表的分页对象
	 */
	public PageVo<ProgramListVo> dbSelectPage(ProgramPageListDto programPageListDto) {
		// 根据program和program_show_time从数据库中连表查询
		IPage<ProgramJoinShowTime> iPage = programMapper.selectPage(
				PageUtil.getPageParams(programPageListDto),
				programPageListDto);

		// 如果查询的节目列表为空，则直接返回pageVo对象
		if (CollectionUtil.isEmpty(iPage.getRecords())) {
			return new PageVo<>(iPage.getCurrent(), iPage.getSize(), iPage.getTotal(), new ArrayList<>());
		}

		// 收集节目类型ID集合
		Set<Long> programCategoryIdList = iPage.getRecords().stream()
				.map(Program::getProgramCategoryId)
				.collect(Collectors.toSet());

		// 收集节目类型id为键，节目类型名为值的映射
		Map<Long, String> programCategoryMap = selectProgramCategoryMap(programCategoryIdList);

		// 收集节目id集合
		List<Long> programIdList = iPage.getRecords().stream()
				.map(Program::getId)
				.collect(Collectors.toList());

		// 收集以节目ID为键，票档对象为值的映射
		Map<Long, TicketCategoryAggregate> ticketCategorieMap = selectTicketCategorieMap(programIdList);

		// 查询区域，获得以地区ID为键，地区名为值的映射
		Map<Long, String> tempAreaMap = new HashMap<>(64);

		// 地区查询请求传递参数
		AreaSelectDto areaSelectDto = new AreaSelectDto();

		// 往地区查询传递参数中设置地区ID列表
		List<Long> areaIds = iPage.getRecords().stream()
				.map(Program::getAreaId)
				.distinct()
				.collect(Collectors.toList());
		areaSelectDto.setIdList(areaIds);

		// 发起RPC调用获得地区VO列表信息
		ApiResponse<List<AreaVo>> areaResponse = baseDataClient.selectByIdList(areaSelectDto);
		if (Objects.equals(areaResponse.getCode(), ApiResponse.ok().getCode())) {
			// 若请求状态码正常
			if (CollectionUtil.isNotEmpty(areaResponse.getData())) {
				// 转换为以地区ID为键，地区名为值的映射
				tempAreaMap = areaResponse.getData().stream()
						.collect(Collectors.toMap(AreaVo::getId, AreaVo::getName, (v1, v2) -> v2));
			}
		}
		else {
			log.error("base-data selectByIdList rpc error areaResponse:{}", JSON.toJSONString(areaResponse));
		}

		Map<Long, String> areaMap = tempAreaMap;
		return PageUtil.convertPage(
				iPage,
				programJoinShowTime -> {
					ProgramListVo programListVo = new ProgramListVo();
					BeanUtil.copyProperties(programJoinShowTime, programListVo);
					//区域名字
					programListVo.setAreaName(areaMap.get(programJoinShowTime.getAreaId()));
					//节目名字
					programListVo.setProgramCategoryName(programCategoryMap.get(programJoinShowTime.getProgramCategoryId()));
					//最低价
					programListVo.setMinPrice(
							Optional.ofNullable(ticketCategorieMap.get(programJoinShowTime.getId()))
									.map(TicketCategoryAggregate::getMinPrice).orElse(null));
					//最高价
					programListVo.setMaxPrice(
							Optional.ofNullable(ticketCategorieMap.get(programJoinShowTime.getId()))
									.map(TicketCategoryAggregate::getMaxPrice).orElse(null));
					return programListVo;
				});
	}

	/**
	 * 查询节目详情
	 *
	 * @param programGetDto 查询节目数据的入参
	 * @return 执行后的结果
	 */
	public ProgramVo detail(ProgramGetDto programGetDto) {
		// 使用组合模式验证参数
		compositeContainer.execute(CompositeCheckType.PROGRAM_DETAIL_CHECK.getValue(), programGetDto);
		return getDetail(programGetDto);
	}

	/**
	 * 查询节目详情V1
	 *
	 * @param programGetDto 查询节目数据的入参
	 * @return 执行后的结果
	 */
	public ProgramVo detailV1(ProgramGetDto programGetDto) {
		compositeContainer.execute(CompositeCheckType.PROGRAM_DETAIL_CHECK.getValue(), programGetDto);
		return getDetail(programGetDto);
	}

	/**
	 * 查询节目详情V2
	 *
	 * @param programGetDto 查询节目数据的入参
	 * @return 执行后的结果
	 */
	public ProgramVo detailV2(ProgramGetDto programGetDto) {
		compositeContainer.execute(CompositeCheckType.PROGRAM_DETAIL_CHECK.getValue(), programGetDto);
		return getDetailV2(programGetDto);
	}

	/**
	 * 查询节目详情执行
	 *
	 * @param programGetDto 查询节目数据的入参
	 * @return 执行后的结果
	 */
	public ProgramVo getDetail(ProgramGetDto programGetDto) {
		// 查询节目演出时间
		Long programIdFromDto = programGetDto.getId();
		ProgramShowTime programShowTime = programShowTimeService.selectProgramShowTimeByProgramId(programIdFromDto);

		// 从节目表获取数据，以及区域信息
		long expireTime = DateUtils.countBetweenSecond(DateUtils.now(), programShowTime.getShowTime());
		ProgramVo programVo = programService.getById(programIdFromDto, expireTime, TimeUnit.SECONDS);
		programVo.setShowTime(programShowTime.getShowTime());
		programVo.setShowDayTime(programShowTime.getShowDayTime());
		programVo.setShowWeekTime(programShowTime.getShowWeekTime());

		// 从节目分组表获取数据
		ProgramGroupVo programGroupVo = programService.getProgramGroup(programVo.getProgramGroupId());
		programVo.setProgramGroupVo(programGroupVo);

		// 预先加载用户购票人
		preloadTicketUserList(programVo.getHighHeat());

		// 预先加载用户下节目订单数量
		Long programId = programVo.getId();
		preloadAccountOrderCount(programId);

		// 设置节目类型相关信息
		ProgramCategory programCategory = getProgramCategory(programVo.getProgramCategoryId());
		if (Objects.nonNull(programCategory)) {
			programVo.setProgramCategoryName(programCategory.getName());
		}

		// 查询节目父级类型相关信息
		ProgramCategory parentProgramCategory = getProgramCategory(programVo.getParentProgramCategoryId());
		if (Objects.nonNull(parentProgramCategory)) {
			programVo.setParentProgramCategoryName(parentProgramCategory.getName());
		}

		// 查询节目票档信息
		List<TicketCategoryVo> ticketCategoryVoList = ticketCategoryService.selectTicketCategoryListByProgramId(programId, expireTime, TimeUnit.SECONDS);
		programVo.setTicketCategoryVoList(ticketCategoryVoList);

		return programVo;
	}

	/**
	 * 查询节目详情V2执行
	 *
	 * @param programGetDto 查询节目数据的入参
	 * @return 执行后的结果
	 */
	/**
	 * 获取节目详情V2版本
	 * 该方法用于获取节目的详细信息，包括节目基本信息、播出时间、票务信息等
	 * 主要通过多个缓存服务来获取数据，以提高响应速度和减少数据库访问压力
	 *
	 * @param programGetDto 节目获取DTO，包含节目ID等查询条件
	 * @return ProgramVo 节目详情对象，包含节目的各种信息
	 */
	public ProgramVo getDetailV2(ProgramGetDto programGetDto) {
		// 根据节目ID查询节目播出时间信息
		ProgramShowTime programShowTime =
				programShowTimeService.selectProgramShowTimeByProgramIdMultipleCache(programGetDto.getId());

		// 根据节目ID和播出时间查询节目基本信息
		ProgramVo programVo = programService.getByIdMultipleCache(programGetDto.getId(), programShowTime.getShowTime());

		// 设置节目播出时间相关信息
		programVo.setShowTime(programShowTime.getShowTime());
		programVo.setShowDayTime(programShowTime.getShowDayTime());
		programVo.setShowWeekTime(programShowTime.getShowWeekTime());

		// 查询并设置节目组信息
		ProgramGroupVo programGroupVo = programService.getProgramGroupMultipleCache(programVo.getProgramGroupId());
		programVo.setProgramGroupVo(programGroupVo);

		// 预加载热门票务用户列表，可能用于后续的票务推荐或显示
		preloadTicketUserList(programVo.getHighHeat());

		// 预加载账户订单数量，可能用于个性化展示或业务逻辑判断
		preloadAccountOrderCount(programVo.getId());

		// 查询并设置节目类别信息
		ProgramCategory programCategory = getProgramCategoryMultipleCache(programVo.getProgramCategoryId());
		if (Objects.nonNull(programCategory)) {
			programVo.setProgramCategoryName(programCategory.getName());
		}
		// 查询并设置父节目类别信息
		ProgramCategory parentProgramCategory = getProgramCategoryMultipleCache(programVo.getParentProgramCategoryId());
		if (Objects.nonNull(parentProgramCategory)) {
			programVo.setParentProgramCategoryName(parentProgramCategory.getName());
		}

		// 查询并设置票务类别列表
		List<TicketCategoryVo> ticketCategoryVoList = ticketCategoryService.selectTicketCategoryListByProgramIdMultipleCache(programVo.getId(), programShowTime.getShowTime());
		programVo.setTicketCategoryVoList(ticketCategoryVoList);

		// 返回填充完毕的节目详情对象
		return programVo;
	}


	/**
	 * 查询节目表详情执行（多级）
	 *
	 * @param programId 节目id
	 * @param showTime  节目演出时间
	 * @return 执行后的结果
	 */
	public ProgramVo getByIdMultipleCache(Long programId, Date showTime) {
		RedisKeyBuild redisKey = RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM, programId);
		return localCacheProgram.getCache(
				redisKey.getRelKey(),
				key -> {
					log.info("查询节目详情 从本地缓存没有查询到 节目id : {}", programId);
					long expireTime = DateUtils.countBetweenSecond(DateUtils.now(), showTime);
					ProgramVo programVo = getById(programId, expireTime, TimeUnit.SECONDS);
					programVo.setShowTime(showTime);
					return programVo;
				});
	}

	/**
	 * 根据节目ID获取节目信息，使用本地缓存和Redis缓存
	 * 首先尝试从本地缓存中获取节目信息，如果未命中，则从Redis缓存中获取
	 *
	 * @param programId 节目ID
	 * @return 节目信息对象，如果未找到则返回null
	 */
	public ProgramVo simpleGetByIdMultipleCache(Long programId) {
		// 创建Redis键
		RedisKeyBuild redisKey = RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM, programId);
		// 尝试从本地缓存中获取节目信息
		ProgramVo programVoCache = localCacheProgram.getCache(redisKey.getRelKey());
		if (Objects.nonNull(programVoCache)) {
			return programVoCache;
		}
		// 如果本地缓存未命中，则从Redis缓存中获取节目信息
		return redisCache.get(redisKey, ProgramVo.class);
	}

	/**
	 * 获取节目的信息和播出时间，使用本地缓存和Redis缓存
	 * 首先获取节目播出时间信息，然后获取节目信息，并将播出时间信息设置到节目信息对象中
	 *
	 * @param programId 节目ID
	 * @return 包含播出时间信息的节目信息对象
	 * @throws DaMaiFrameException 如果未找到节目播出时间或节目信息，则抛出异常
	 */
	public ProgramVo simpleGetProgramAndShowMultipleCache(Long programId) {
		// 获取节目播出时间信息
		ProgramShowTime programShowTime = programShowTimeService.simpleSelectProgramShowTimeByProgramIdMultipleCache(programId);

		// 如果未找到节目播出时间，则抛出异常
		if (Objects.isNull(programShowTime)) {
			throw new DaMaiFrameException(BaseCode.PROGRAM_SHOW_TIME_NOT_EXIST);
		}

		// 获取节目信息
		ProgramVo programVo = simpleGetByIdMultipleCache(programId);

		// 如果未找到节目信息，则抛出异常
		if (Objects.isNull(programVo)) {
			throw new DaMaiFrameException(BaseCode.PROGRAM_NOT_EXIST);
		}

		// 将播出时间信息设置到节目信息对象中
		programVo.setShowTime(programShowTime.getShowTime());
		programVo.setShowDayTime(programShowTime.getShowDayTime());
		programVo.setShowWeekTime(programShowTime.getShowWeekTime());

		return programVo;
	}

	/**
	 * 根据节目ID获取节目详细信息
	 * 本方法使用了两个锁，一个是方法级别的ServiceLock，读锁，另一个是在缓存未命中时才加的RLock
	 * 首先尝试从Redis缓存中获取节目信息.
	 * 如果缓存未命中，则需要加分布式锁来保证数据的一致性：避免在高并发场景下出现多个线程重复创建同一份节目信息、缓存不一致等问题
	 * 线程在获取RLock锁之后，再次尝试从Redis获取数据，如果仍然没有，则调用创建节目信息的方法
	 * 并将结果缓存到Redis中
	 *
	 * @param programId  节目ID
	 * @param expireTime 缓存过期时间
	 * @param timeUnit   缓存过期时间单位
	 * @return 节目详细信息对象
	 */
	@ServiceLock(lockType = LockType.Read, name = PROGRAM_LOCK, keys = {"#programId"})
	public ProgramVo getById(Long programId, Long expireTime, TimeUnit timeUnit) {
		// 尝试从Redis缓存中获取节目信息
		RedisKeyBuild redisKey = RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM, programId);
		ProgramVo programVo = redisCache.get(redisKey, ProgramVo.class);

		// 如果缓存中存在节目信息，则直接返回
		if (Objects.nonNull(programVo)) {
			return programVo;
		}

		// 记录日志，表示从Redis缓存中未查询到节目详情
		log.info("查询节目详情 从Redis缓存没有查询到 节目id : {}", programId);

		// 获取服务端锁，确保接下来的操作数据一致性
		RLock lock = serviceLockTool.getLock(LockType.Reentrant, GET_PROGRAM_LOCK, new String[]{String.valueOf(programId)});

		// 加锁，避免并发环境下多个线程重复创建同一份节目信息造成的缓存不一致问题
		lock.lock();
		try {
			// 再次尝试从Redis获取节目信息，如果仍然没有，则调用创建节目信息的方法
			// 并将结果缓存到Redis中
			return redisCache.get(
					redisKey,
					ProgramVo.class,
					() -> createProgramVo(programId),
					expireTime,
					timeUnit);
		}
		finally {
			// 释放锁，确保其他线程可以继续执行
			lock.unlock();
		}
	}

	/**
	 * 根据节目组ID获取节目组信息，使用本地缓存和Redis缓存双重缓存策略
	 * 首先尝试从本地缓存中获取数据，如果未命中，则调用getProgramGroup方法从Redis缓存或数据库中获取数据
	 *
	 * @param programGroupId 节目组ID
	 * @return 节目组信息对象
	 */
	public ProgramGroupVo getProgramGroupMultipleCache(Long programGroupId) {
		return localCacheProgramGroup.getCache(
				RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_GROUP, programGroupId).getRelKey(),
				key -> getProgramGroup(programGroupId)
		);
	}

	/**
	 * 获取节目组信息，使用服务端锁防止并发情况下出现缓存穿透
	 * 首先尝试从Redis缓存中获取数据，如果未命中，则加锁后再次尝试从Redis中获取数据
	 * 如果数据仍然未命中，则从数据库中查询并放入缓存
	 *
	 * @param programGroupId 节目组ID
	 * @return 节目组信息对象
	 */
	@ServiceLock(lockType = LockType.Read, name = PROGRAM_GROUP_LOCK, keys = {"#programGroupId"})
	public ProgramGroupVo getProgramGroup(Long programGroupId) {
		// 构建Redis缓存键
		RedisKeyBuild redisKey = RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_GROUP, programGroupId);

		// 尝试从Redis缓存中获取节目组信息
		ProgramGroupVo programGroupVo = redisCache.get(redisKey, ProgramGroupVo.class);

		// 如果缓存中存在节目组信息，则直接返回
		if (Objects.nonNull(programGroupVo)) {
			return programGroupVo;
		}

		// 如果缓存中不存在节目组信息，则需要加分布式锁来保证数据的一致性
		RLock lock = serviceLockTool.getLock(LockType.Reentrant, GET_PROGRAM_LOCK, new String[]{String.valueOf(programGroupId)});

		// 加锁，避免并发环境下多个线程重复创建同一份节目组信息造成的缓存不一致问题
		lock.lock();

		try {
			// 再次尝试从Redis获取节目组信息，如果仍然没有，则调用创建节目组信息的方法
			programGroupVo = redisCache.get(redisKey, ProgramGroupVo.class);

			// 如果数据仍然未命中（说明是缓存失效后第一个进入此代码块的线程），则从数据库中查询并放入缓存
			if (Objects.isNull(programGroupVo)) {
				// 从数据库中查询并放入缓存
				programGroupVo = createProgramGroupVo(programGroupId);
				long ttl = DateUtils.countBetweenSecond(DateUtils.now(), programGroupVo.getRecentShowTime());
				redisCache.set(redisKey, programGroupVo, ttl, TimeUnit.SECONDS);
			}
			// 返回结果
			return programGroupVo;
		}
		finally {
			// 释放锁，确保其他线程可以继续执行
			lock.unlock();
		}
	}

	/**
	 * 根据节目类别ID列表选择节目类别映射
	 * 以映射的形式返回，以便于在其他地方快速查找和使用这些信息
	 * 节目类别ID列表 => 以节目类别ID为键，节目类别名称为值的哈希表
	 *
	 * @param programCategoryIdList 节目类别ID列表，用于指定需要查询的节目类别的范围
	 * @return 返回一个映射，键为节目类别ID，值为节目类别名称
	 */
	public Map<Long, String> selectProgramCategoryMap(Collection<Long> programCategoryIdList) {
		// 创建一个查询包装器，用于构建针对ProgramCategory表的查询条件
		LambdaQueryWrapper<ProgramCategory> pcLambdaQueryWrapper = Wrappers.lambdaQuery(ProgramCategory.class)
				.in(ProgramCategory::getId, programCategoryIdList);

		// 执行查询，获取符合条件的节目类别列表
		List<ProgramCategory> programCategoryList = programCategoryMapper.selectList(pcLambdaQueryWrapper);

		// 将查询结果转换为映射返回，便于后续快速查找
		return programCategoryList
				.stream()
				.collect(Collectors.toMap(ProgramCategory::getId, ProgramCategory::getName, (v1, v2) -> v2));
	}

	/**
	 * 根据节目ID列表选择票务类别映射
	 * 此方法通过查询数据库获取票务类别列表，并将其转换为一个映射，以便于根据节目ID快速查找票务类别信息
	 *
	 * @param programIdList 节目ID列表，用于查询票务类别信息
	 * @return 返回一个映射，键为节目ID，值为对应的票档统计对象
	 */
	public Map<Long, TicketCategoryAggregate> selectTicketCategorieMap(List<Long> programIdList) {
		// 查询数据库，获取票务类别列表
		List<TicketCategoryAggregate> ticketCategorieList = ticketCategoryMapper.selectAggregateList(programIdList);

		// 将票务类别列表转换为映射，便于根据节目ID快速查找
		return ticketCategorieList
				.stream()
				.collect(Collectors.toMap(TicketCategoryAggregate::getProgramId, ticketCategory -> ticketCategory, (v1, v2) -> v2));
	}

	/**
	 * 根据节目操作数据DTO操作节目数据
	 * 此方法首先验证指定的座位是否存在并检查其销售状态，然后更新座位的销售状态
	 * 最后，更新票务类别的剩余数量
	 *
	 * @param programOperateDataDto 节目操作数据DTO，包含节目ID、票务类别计数列表和座位ID列表
	 * @throws DaMaiFrameException 如果座位不存在、座位已售出或更新操作失败，则抛出异常
	 */
	@RepeatExecuteLimit(name = CANCEL_PROGRAM_ORDER, keys = {"#programOperateDataDto.programId", "#programOperateDataDto.seatIdList"})
	@Transactional(rollbackFor = Exception.class)
	public void operateProgramData(ProgramOperateDataDto programOperateDataDto) {
		// 获取票务类别计数列表和座位ID列表
		List<TicketCategoryCountDto> ticketCategoryCountDtoList = programOperateDataDto.getTicketCategoryCountDtoList();
		List<Long> seatIdList = programOperateDataDto.getSeatIdList();

		// 查询指定节目ID和座位ID列表的座位信息
		LambdaQueryWrapper<Seat> seatLambdaQueryWrapper =
				Wrappers.lambdaQuery(Seat.class)
						.eq(Seat::getProgramId, programOperateDataDto.getProgramId())
						.in(Seat::getId, seatIdList);
		List<Seat> seatList = seatMapper.selectList(seatLambdaQueryWrapper);

		// 验证座位是否存在
		if (CollectionUtil.isEmpty(seatList)) {
			throw new DaMaiFrameException(BaseCode.SEAT_NOT_EXIST);
		}

		// 验证查询到的座位数量是否与提供的座位ID数量一致
		if (seatList.size() != seatIdList.size()) {
			throw new DaMaiFrameException(BaseCode.SEAT_UPDATE_REL_COUNT_NOT_EQUAL_PRESET_COUNT);
		}

		// 检查座位是否已售出
		for (Seat seat : seatList) {
			if (Objects.equals(seat.getSellStatus(), SellStatus.SOLD.getCode())) {
				throw new DaMaiFrameException(BaseCode.SEAT_SOLD);
			}
		}

		// 更新座位的销售状态为已售出
		LambdaUpdateWrapper<Seat> seatLambdaUpdateWrapper =
				Wrappers.lambdaUpdate(Seat.class)
						.eq(Seat::getProgramId, programOperateDataDto.getProgramId())
						.in(Seat::getId, seatIdList);
		Seat updateSeat = new Seat();
		updateSeat.setSellStatus(SellStatus.SOLD.getCode());
		seatMapper.update(updateSeat, seatLambdaUpdateWrapper);

		// 批量更新票务类别的剩余数量
		int updateRemainNumberCount =
				ticketCategoryMapper.batchUpdateRemainNumber(ticketCategoryCountDtoList, programOperateDataDto.getProgramId());

		// 验证更新的票务类别数量是否正确
		if (updateRemainNumberCount != ticketCategoryCountDtoList.size()) {
			throw new DaMaiFrameException(BaseCode.UPDATE_TICKET_CATEGORY_COUNT_NOT_CORRECT);
		}
	}

	/**
	 * 根据节目ID创建ProgramVo对象
	 * 此方法首先通过节目ID从数据库中获取节目信息，然后将信息复制到ProgramVo对象中
	 * 如果节目不存在，则抛出异常如果节目存在，它还会根据节目中的区域ID通过RPC调用获取区域信息，
	 * 并将区域名称设置在ProgramVo对象中
	 *
	 * @param programId 节目ID，用于查询节目信息
	 * @return 返回填充了节目信息和区域名称的ProgramVo对象
	 * @throws DaMaiFrameException 如果节目不存在，则抛出此异常
	 */
	private ProgramVo createProgramVo(Long programId) {
		// 初始化ProgramVo对象
		ProgramVo programVo = new ProgramVo();

		// 通过programId获取Program对象，如果不存在，则抛出异常
		Program program = Optional
				.ofNullable(programMapper.selectById(programId))
				.orElseThrow(() -> new DaMaiFrameException(BaseCode.PROGRAM_NOT_EXIST));

		// 将Program对象的属性复制到ProgramVo对象中
		BeanUtil.copyProperties(program, programVo);

		// 初始化AreaGetDto对象，并设置区域ID
		AreaGetDto areaGetDto = new AreaGetDto();
		areaGetDto.setId(program.getAreaId());

		// 通过RPC调用获取区域信息
		ApiResponse<AreaVo> areaResponse = baseDataClient.getById(areaGetDto);

		// 如果RPC调用成功且返回的数据不为空，则将区域名称设置在ProgramVo对象中
		if (Objects.equals(areaResponse.getCode(), ApiResponse.ok().getCode())) {
			if (Objects.nonNull(areaResponse.getData())) {
				programVo.setAreaName(areaResponse.getData().getName());
			}
		}
		// 如果RPC调用失败，记录错误日志
		else {
			log.error("base-data rpc getById error areaResponse:{}", JSON.toJSONString(areaResponse));
		}

		// 返回填充了信息的ProgramVo对象
		return programVo;
	}

	private ProgramGroupVo createProgramGroupVo(Long programGroupId) {
		ProgramGroupVo programGroupVo = new ProgramGroupVo();
		ProgramGroup programGroup =
				Optional.ofNullable(programGroupMapper.selectById(programGroupId))
						.orElseThrow(() -> new DaMaiFrameException(BaseCode.PROGRAM_GROUP_NOT_EXIST));
		programGroupVo.setId(programGroup.getId());
		programGroupVo.setProgramSimpleInfoVoList(JSON.parseArray(programGroup.getProgramJson(), ProgramSimpleInfoVo.class));
		programGroupVo.setRecentShowTime(programGroup.getRecentShowTime());
		return programGroupVo;
	}

	public List<Long> getAllProgramIdList() {
		LambdaQueryWrapper<Program> programLambdaQueryWrapper =
				Wrappers.lambdaQuery(Program.class).eq(Program::getProgramStatus, BusinessStatus.YES.getCode())
						.select(Program::getId);
		List<Program> programs = programMapper.selectList(programLambdaQueryWrapper);
		return programs.stream().map(Program::getId).collect(Collectors.toList());
	}

	public ProgramVo getDetailFromDb(Long programId) {
		ProgramVo programVo = createProgramVo(programId);

		ProgramCategory programCategory = getProgramCategory(programVo.getProgramCategoryId());
		if (Objects.nonNull(programCategory)) {
			programVo.setProgramCategoryName(programCategory.getName());
		}
		ProgramCategory parentProgramCategory = getProgramCategory(programVo.getParentProgramCategoryId());
		if (Objects.nonNull(parentProgramCategory)) {
			programVo.setParentProgramCategoryName(parentProgramCategory.getName());
		}

		LambdaQueryWrapper<ProgramShowTime> programShowTimeLambdaQueryWrapper =
				Wrappers.lambdaQuery(ProgramShowTime.class).eq(ProgramShowTime::getProgramId, programId);
		ProgramShowTime programShowTime = Optional.ofNullable(programShowTimeMapper.selectOne(programShowTimeLambdaQueryWrapper))
				.orElseThrow(() -> new DaMaiFrameException(BaseCode.PROGRAM_SHOW_TIME_NOT_EXIST));

		programVo.setShowTime(programShowTime.getShowTime());
		programVo.setShowDayTime(programShowTime.getShowDayTime());
		programVo.setShowWeekTime(programShowTime.getShowWeekTime());

		return programVo;
	}

	/**
	 * 预加载购票用户列表到缓存中
	 * 此方法旨在为用户购买票证时，预先加载用户列表到Redis缓存，以提高访问效率
	 *
	 * @param highHeat 业务状态码，用于判断是否需要执行预加载操作
	 */
	private void preloadTicketUserList(Integer highHeat) {
		// 检查业务状态码是否指示不需要执行预加载操作
		if (Objects.equals(highHeat, BusinessStatus.NO.getCode())) {
			return;
		}

		// 获取用户ID和认证码
		String userId = BaseParameterHolder.getParameter(USER_ID);
		String code = BaseParameterHolder.getParameter(CODE);

		// 如果用户id或者code有一个为空，那么判断不了用户登录状态，也不用预先加载了
		if (StringUtil.isEmpty(userId) || StringUtil.isEmpty(code)) {
			return;
		}

		// 如果用户没有登录，也不用预先加载了
		Boolean userLogin = redisCache.hasKey(RedisKeyBuild.createRedisKey(RedisKeyManage.USER_LOGIN, code, userId));
		if (!userLogin) {
			return;
		}

		// 异步加载购票人信息，别耽误查询节目详情的主线程
		BusinessThreadPool.execute(() -> {
			try {
				// 如果已经预热加载了，就不用再执行了
				Boolean hasPreloaded = redisCache.hasKey(RedisKeyBuild.createRedisKey(RedisKeyManage.TICKET_USER_LIST, userId));
				if (!hasPreloaded) {
					// 创建请求对象并设置用户ID
					TicketUserListDto ticketUserListDto = new TicketUserListDto();
					ticketUserListDto.setUserId(Long.parseLong(userId));

					// 调用用户服务获取购票用户列表
					ApiResponse<List<TicketUserVo>> apiResponse = userClient.list(ticketUserListDto);

					// 检查服务调用是否成功
					if (Objects.equals(apiResponse.getCode(), BaseCode.SUCCESS.getCode())) {
						// 将获取到的用户列表存储到缓存中
						Optional.ofNullable(apiResponse.getData())
								.filter(CollectionUtil::isNotEmpty)
								.ifPresent(ticketUserVoList ->
										redisCache.set(RedisKeyBuild.createRedisKey(RedisKeyManage.TICKET_USER_LIST, userId), ticketUserVoList));
					}
					else {
						// 记录服务调用失败的日志
						log.warn("userClient.select 调用失败 apiResponse : {}", JSON.toJSONString(apiResponse));
					}
				}
			}
			catch (Exception e) {
				// 记录预加载操作中的异常
				log.error("预热加载购票人列表失败", e);
			}
		});
	}


	/**
	 * 预加载账户订单数量
	 * 此方法旨在预先加载用户账户的订单数量到缓存中，以提高后续查询效率
	 * 它首先检查必要的参数是否可用，然后验证用户登录状态，最后在后台线程中执行实际的订单数量查询和缓存操作
	 *
	 * @param programId 程序ID，用于指定查询的程序
	 */
	private void preloadAccountOrderCount(Long programId) {
		// 获取用户ID和验证码，用于后续的验证和查询
		String userId = BaseParameterHolder.getParameter(USER_ID);
		String code = BaseParameterHolder.getParameter(CODE);

		// 如果用户ID或验证码为空，则直接返回，不执行后续操作
		if (StringUtil.isEmpty(userId) || StringUtil.isEmpty(code)) {
			return;
		}

		// 检查用户登录状态，如果未登录，则直接返回
		Boolean userLogin =
				redisCache.hasKey(RedisKeyBuild.createRedisKey(RedisKeyManage.USER_LOGIN, code, userId));
		if (!userLogin) {
			return;
		}

		// 使用业务线程池执行预加载操作，避免阻塞当前线程
		BusinessThreadPool.execute(() -> {
			try {
				// 如果缓存中不存在账户订单数量，则进行查询并缓存结果
				RedisKeyBuild redisKey = RedisKeyBuild.createRedisKey(RedisKeyManage.ACCOUNT_ORDER_COUNT, userId, programId);
				if (!redisCache.hasKey(redisKey)) {
					AccountOrderCountDto accountOrderCountDto = new AccountOrderCountDto();
					accountOrderCountDto.setUserId(Long.parseLong(userId));
					accountOrderCountDto.setProgramId(programId);

					// 调用订单客户端获取账户订单数量
					ApiResponse<AccountOrderCountVo> apiResponse = orderClient.accountOrderCount(accountOrderCountDto);

					// 如果调用成功，则将结果缓存
					if (Objects.equals(apiResponse.getCode(), BaseCode.SUCCESS.getCode())) {
						Optional.ofNullable(apiResponse.getData())
								.ifPresent(accountOrderCountVo -> {
									Integer count = accountOrderCountVo.getCount();
									long ttl = tokenExpireManager.getTokenExpireTime() + 1;
									redisCache.set(redisKey, count, ttl, TimeUnit.MINUTES);
								});
					}
					// 如果调用失败，则记录日志
					else {
						log.warn("orderClient.accountOrderCount 调用失败 apiResponse : {}", JSON.toJSONString(apiResponse));
					}
				}
			}
			// 捕获并记录任何在执行过程中发生的异常
			catch (Exception e) {
				log.error("预热加载账户订单数量失败", e);
			}
		});
	}

	public ProgramCategory getProgramCategoryMultipleCache(Long programCategoryId) {
		return localCacheProgramCategory.get(String.valueOf(programCategoryId),
				key -> getProgramCategory(programCategoryId));
	}

	public ProgramCategory getProgramCategory(Long programCategoryId) {
		return programCategoryService.getProgramCategory(programCategoryId);
	}

	@Transactional(rollbackFor = Exception.class)
	public Boolean resetExecute(ProgramResetExecuteDto programResetExecuteDto) {
		Long programId = programResetExecuteDto.getProgramId();
		LambdaQueryWrapper<Seat> seatQueryWrapper =
				Wrappers.lambdaQuery(Seat.class).eq(Seat::getProgramId, programId)
						.in(Seat::getSellStatus, SellStatus.LOCK.getCode(), SellStatus.SOLD.getCode());
		List<Seat> seatList = seatMapper.selectList(seatQueryWrapper);
		if (CollectionUtil.isEmpty(seatList)) {
			return true;
		}
		LambdaUpdateWrapper<Seat> seatUpdateWrapper =
				Wrappers.lambdaUpdate(Seat.class).eq(Seat::getProgramId, programId);
		Seat seatUpdate = new Seat();
		seatUpdate.setSellStatus(SellStatus.NO_SOLD.getCode());
		seatMapper.update(seatUpdate, seatUpdateWrapper);

		LambdaQueryWrapper<TicketCategory> ticketCategoryQueryWrapper =
				Wrappers.lambdaQuery(TicketCategory.class).eq(TicketCategory::getProgramId, programId);
		List<TicketCategory> ticketCategories = ticketCategoryMapper.selectList(ticketCategoryQueryWrapper);
		for (TicketCategory ticketCategory : ticketCategories) {
			Long remainNumber = ticketCategory.getRemainNumber();
			Long totalNumber = ticketCategory.getTotalNumber();
			if (!(remainNumber.equals(totalNumber))) {
				TicketCategory ticketCategoryUpdate = new TicketCategory();
				ticketCategoryUpdate.setRemainNumber(totalNumber);

				LambdaUpdateWrapper<TicketCategory> ticketCategoryUpdateWrapper =
						Wrappers.lambdaUpdate(TicketCategory.class)
								.eq(TicketCategory::getProgramId, programId)
								.eq(TicketCategory::getId, ticketCategory.getId());
				ticketCategoryMapper.update(ticketCategoryUpdate, ticketCategoryUpdateWrapper);
			}
		}
		delRedisData(programId);
		delLocalCache(programId);
		return true;
	}

	public void delRedisData(Long programId) {
		Program program = Optional.ofNullable(programMapper.selectById(programId))
				.orElseThrow(() -> new DaMaiFrameException(BaseCode.PROGRAM_NOT_EXIST));
		List<String> keys = new ArrayList<>();
		keys.add(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM, programId).getRelKey());
		keys.add(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_GROUP, program.getProgramGroupId()).getRelKey());
		keys.add(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SHOW_TIME, programId).getRelKey());
		keys.add(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SEAT_NO_SOLD_RESOLUTION_HASH, programId, "*").getRelKey());
		keys.add(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SEAT_LOCK_RESOLUTION_HASH, programId, "*").getRelKey());
		keys.add(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SEAT_SOLD_RESOLUTION_HASH, programId, "*").getRelKey());
		keys.add(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_TICKET_CATEGORY_LIST, programId).getRelKey());
		keys.add(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_TICKET_REMAIN_NUMBER_HASH_RESOLUTION, programId, "*").getRelKey());
		programDelCacheData.del(keys, new String[]{});
	}

	public Boolean invalid(final ProgramInvalidDto programInvalidDto) {
		Program program = new Program();
		program.setId(programInvalidDto.getId());
		program.setProgramStatus(BusinessStatus.NO.getCode());
		int result = programMapper.updateById(program);
		if (result > 0) {
			delRedisData(programInvalidDto.getId());
			redisStreamPushHandler.push(String.valueOf(programInvalidDto.getId()));
			programEs.deleteByProgramId(programInvalidDto.getId());
			return true;
		}
		else {
			return false;
		}
	}

	public ProgramVo localDetail(final ProgramGetDto programGetDto) {
		return localCacheProgram.getCache(String.valueOf(programGetDto.getId()));
	}

	public void delLocalCache(Long programId) {
		log.info("删除本地缓存 programId : {}", programId);
		localCacheProgram.del(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM, programId).getRelKey());
		localCacheProgramGroup.del(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_GROUP, programId).getRelKey());
		localCacheProgramShowTime.del(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SHOW_TIME, programId).getRelKey());
		localCacheTicketCategory.del(programId);
	}
}

