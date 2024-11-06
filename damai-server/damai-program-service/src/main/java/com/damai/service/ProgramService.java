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
        //将入参的参数进行具体的组装
        setQueryTime(programSearchDto);
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
     * 组装节目参数
     *
     * @param programPageListDto 节目数据的入参
     */
    public void setQueryTime(ProgramPageListDto programPageListDto) {
        switch (programPageListDto.getTimeType()) {
            case ProgramTimeType.TODAY:
                programPageListDto.setStartDateTime(DateUtils.now(FORMAT_DATE));
                programPageListDto.setEndDateTime(DateUtils.now(FORMAT_DATE));
                break;
            case ProgramTimeType.TOMORROW:
                programPageListDto.setStartDateTime(DateUtils.now(FORMAT_DATE));
                programPageListDto.setEndDateTime(DateUtils.addDay(DateUtils.now(FORMAT_DATE), 1));
                break;
            case ProgramTimeType.WEEK:
                programPageListDto.setStartDateTime(DateUtils.now(FORMAT_DATE));
                programPageListDto.setEndDateTime(DateUtils.addWeek(DateUtils.now(FORMAT_DATE), 1));
                break;
            case ProgramTimeType.MONTH:
                programPageListDto.setStartDateTime(DateUtils.now(FORMAT_DATE));
                programPageListDto.setEndDateTime(DateUtils.addMonth(DateUtils.now(FORMAT_DATE), 1));
                break;
            case ProgramTimeType.CALENDAR:
                if (Objects.isNull(programPageListDto.getStartDateTime())) {
                    throw new DaMaiFrameException(BaseCode.START_DATE_TIME_NOT_EXIST);
                }
                if (Objects.isNull(programPageListDto.getEndDateTime())) {
                    throw new DaMaiFrameException(BaseCode.END_DATE_TIME_NOT_EXIST);
                }
                break;
            default:
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
     * 查询分类信息（数据库查询）
     *
     * @param programPageListDto 查询节目数据的入参
     * @return 执行后的结果
     */
    public PageVo<ProgramListVo> dbSelectPage(ProgramPageListDto programPageListDto) {
        IPage<ProgramJoinShowTime> iPage =
                programMapper.selectPage(PageUtil.getPageParams(programPageListDto), programPageListDto);
        if (CollectionUtil.isEmpty(iPage.getRecords())) {
            return new PageVo<>(iPage.getCurrent(), iPage.getSize(), iPage.getTotal(), new ArrayList<>());
        }
        Set<Long> programCategoryIdList =
                iPage.getRecords().stream().map(Program::getProgramCategoryId).collect(Collectors.toSet());
        Map<Long, String> programCategoryMap = selectProgramCategoryMap(programCategoryIdList);

        List<Long> programIdList = iPage.getRecords().stream().map(Program::getId).collect(Collectors.toList());
        Map<Long, TicketCategoryAggregate> ticketCategorieMap = selectTicketCategorieMap(programIdList);

        Map<Long, String> tempAreaMap = new HashMap<>(64);
        AreaSelectDto areaSelectDto = new AreaSelectDto();
        areaSelectDto.setIdList(iPage.getRecords().stream().map(Program::getAreaId).distinct().collect(Collectors.toList()));
        ApiResponse<List<AreaVo>> areaResponse = baseDataClient.selectByIdList(areaSelectDto);
        if (Objects.equals(areaResponse.getCode(), ApiResponse.ok().getCode())) {
            if (CollectionUtil.isNotEmpty(areaResponse.getData())) {
                tempAreaMap = areaResponse.getData().stream()
                        .collect(Collectors.toMap(AreaVo::getId, AreaVo::getName, (v1, v2) -> v2));
            }
        }
        else {
            log.error("base-data selectByIdList rpc error areaResponse:{}", JSON.toJSONString(areaResponse));
        }
        Map<Long, String> areaMap = tempAreaMap;

        return PageUtil.convertPage(iPage, programJoinShowTime -> {
            ProgramListVo programListVo = new ProgramListVo();
            BeanUtil.copyProperties(programJoinShowTime, programListVo);

            programListVo.setAreaName(areaMap.get(programJoinShowTime.getAreaId()));
            programListVo.setProgramCategoryName(programCategoryMap.get(programJoinShowTime.getProgramCategoryId()));
            programListVo.setMinPrice(Optional.ofNullable(ticketCategorieMap.get(programJoinShowTime.getId()))
                    .map(TicketCategoryAggregate::getMinPrice).orElse(null));
            programListVo.setMaxPrice(Optional.ofNullable(ticketCategorieMap.get(programJoinShowTime.getId()))
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
        ProgramShowTime programShowTime = programShowTimeService.selectProgramShowTimeByProgramId(programGetDto.getId());
        ProgramVo programVo = programService.getById(programGetDto.getId(), DateUtils.countBetweenSecond(DateUtils.now(),
                programShowTime.getShowTime()), TimeUnit.SECONDS);
        programVo.setShowTime(programShowTime.getShowTime());
        programVo.setShowDayTime(programShowTime.getShowDayTime());
        programVo.setShowWeekTime(programShowTime.getShowWeekTime());

        ProgramGroupVo programGroupVo = programService.getProgramGroup(programVo.getProgramGroupId());
        programVo.setProgramGroupVo(programGroupVo);

        preloadTicketUserList(programVo.getHighHeat());

        preloadAccountOrderCount(programVo.getId());

        ProgramCategory programCategory = getProgramCategory(programVo.getProgramCategoryId());
        if (Objects.nonNull(programCategory)) {
            programVo.setProgramCategoryName(programCategory.getName());
        }
        ProgramCategory parentProgramCategory = getProgramCategory(programVo.getParentProgramCategoryId());
        if (Objects.nonNull(parentProgramCategory)) {
            programVo.setParentProgramCategoryName(parentProgramCategory.getName());
        }

        List<TicketCategoryVo> ticketCategoryVoList =
                ticketCategoryService.selectTicketCategoryListByProgramId(programVo.getId(),
                        DateUtils.countBetweenSecond(DateUtils.now(), programShowTime.getShowTime()), TimeUnit.SECONDS);
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
        List<TicketCategoryVo> ticketCategoryVoList = ticketCategoryService
                .selectTicketCategoryListByProgramIdMultipleCache(programVo.getId(), programShowTime.getShowTime());
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
        return localCacheProgram.getCache(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM, programId).getRelKey(),
                key -> {
                    log.info("查询节目详情 从本地缓存没有查询到 节目id : {}", programId);
                    ProgramVo programVo = getById(programId, DateUtils.countBetweenSecond(DateUtils.now(), showTime),
                            TimeUnit.SECONDS);
                    programVo.setShowTime(showTime);
                    return programVo;
                });
    }

    public ProgramVo simpleGetByIdMultipleCache(Long programId) {
        ProgramVo programVoCache = localCacheProgram.getCache(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM,
                programId).getRelKey());
        if (Objects.nonNull(programVoCache)) {
            return programVoCache;
        }
        return redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM, programId), ProgramVo.class);
    }

    public ProgramVo simpleGetProgramAndShowMultipleCache(Long programId) {
        ProgramShowTime programShowTime =
                programShowTimeService.simpleSelectProgramShowTimeByProgramIdMultipleCache(programId);
        if (Objects.isNull(programShowTime)) {
            throw new DaMaiFrameException(BaseCode.PROGRAM_SHOW_TIME_NOT_EXIST);
        }

        ProgramVo programVo = simpleGetByIdMultipleCache(programId);
        if (Objects.isNull(programVo)) {
            throw new DaMaiFrameException(BaseCode.PROGRAM_NOT_EXIST);
        }

        programVo.setShowTime(programShowTime.getShowTime());
        programVo.setShowDayTime(programShowTime.getShowDayTime());
        programVo.setShowWeekTime(programShowTime.getShowWeekTime());

        return programVo;
    }

    @ServiceLock(lockType = LockType.Read, name = PROGRAM_LOCK, keys = {"#programId"})
    public ProgramVo getById(Long programId, Long expireTime, TimeUnit timeUnit) {
        ProgramVo programVo =
                redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM, programId), ProgramVo.class);
        if (Objects.nonNull(programVo)) {
            return programVo;
        }
        log.info("查询节目详情 从Redis缓存没有查询到 节目id : {}", programId);
        RLock lock = serviceLockTool.getLock(LockType.Reentrant, GET_PROGRAM_LOCK, new String[]{String.valueOf(programId)});
        lock.lock();
        try {
            return redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM, programId)
                    , ProgramVo.class,
                    () -> createProgramVo(programId)
                    , expireTime,
                    timeUnit);
        }
        finally {
            lock.unlock();
        }
    }

    public ProgramGroupVo getProgramGroupMultipleCache(Long programGroupId) {
        return localCacheProgramGroup.getCache(
                RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_GROUP, programGroupId).getRelKey(),
                key -> getProgramGroup(programGroupId));
    }

    @ServiceLock(lockType = LockType.Read, name = PROGRAM_GROUP_LOCK, keys = {"#programGroupId"})
    public ProgramGroupVo getProgramGroup(Long programGroupId) {
        ProgramGroupVo programGroupVo =
                redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_GROUP, programGroupId), ProgramGroupVo.class);
        if (Objects.nonNull(programGroupVo)) {
            return programGroupVo;
        }
        RLock lock = serviceLockTool.getLock(LockType.Reentrant, GET_PROGRAM_LOCK, new String[]{String.valueOf(programGroupId)});
        lock.lock();
        try {
            programGroupVo = redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_GROUP, programGroupId),
                    ProgramGroupVo.class);
            if (Objects.isNull(programGroupVo)) {
                programGroupVo = createProgramGroupVo(programGroupId);
                redisCache.set(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_GROUP, programGroupId), programGroupVo,
                        DateUtils.countBetweenSecond(DateUtils.now(), programGroupVo.getRecentShowTime()), TimeUnit.SECONDS);
            }
            return programGroupVo;
        }
        finally {
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

    @RepeatExecuteLimit(name = CANCEL_PROGRAM_ORDER, keys = {"#programOperateDataDto.programId"})
    @Transactional(rollbackFor = Exception.class)
    public void operateProgramData(ProgramOperateDataDto programOperateDataDto) {
        List<TicketCategoryCountDto> ticketCategoryCountDtoList = programOperateDataDto.getTicketCategoryCountDtoList();
        List<Long> seatIdList = programOperateDataDto.getSeatIdList();
        LambdaQueryWrapper<Seat> seatLambdaQueryWrapper =
                Wrappers.lambdaQuery(Seat.class)
                        .eq(Seat::getProgramId, programOperateDataDto.getProgramId())
                        .in(Seat::getId, seatIdList);
        List<Seat> seatList = seatMapper.selectList(seatLambdaQueryWrapper);
        if (CollectionUtil.isEmpty(seatList)) {
            throw new DaMaiFrameException(BaseCode.SEAT_NOT_EXIST);
        }
        if (seatList.size() != seatIdList.size()) {
            throw new DaMaiFrameException(BaseCode.SEAT_UPDATE_REL_COUNT_NOT_EQUAL_PRESET_COUNT);
        }
        for (Seat seat : seatList) {
            if (Objects.equals(seat.getSellStatus(), SellStatus.SOLD.getCode())) {
                throw new DaMaiFrameException(BaseCode.SEAT_SOLD);
            }
        }
        LambdaUpdateWrapper<Seat> seatLambdaUpdateWrapper =
                Wrappers.lambdaUpdate(Seat.class)
                        .eq(Seat::getProgramId, programOperateDataDto.getProgramId())
                        .in(Seat::getId, seatIdList);
        Seat updateSeat = new Seat();
        updateSeat.setSellStatus(SellStatus.SOLD.getCode());
        seatMapper.update(updateSeat, seatLambdaUpdateWrapper);

        int updateRemainNumberCount =
                ticketCategoryMapper.batchUpdateRemainNumber(ticketCategoryCountDtoList, programOperateDataDto.getProgramId());
        if (updateRemainNumberCount != ticketCategoryCountDtoList.size()) {
            throw new DaMaiFrameException(BaseCode.UPDATE_TICKET_CATEGORY_COUNT_NOT_CORRECT);
        }
    }

    private ProgramVo createProgramVo(Long programId) {
        ProgramVo programVo = new ProgramVo();
        Program program =
                Optional.ofNullable(programMapper.selectById(programId))
                        .orElseThrow(() -> new DaMaiFrameException(BaseCode.PROGRAM_NOT_EXIST));
        BeanUtil.copyProperties(program, programVo);
        AreaGetDto areaGetDto = new AreaGetDto();
        areaGetDto.setId(program.getAreaId());
        ApiResponse<AreaVo> areaResponse = baseDataClient.getById(areaGetDto);
        if (Objects.equals(areaResponse.getCode(), ApiResponse.ok().getCode())) {
            if (Objects.nonNull(areaResponse.getData())) {
                programVo.setAreaName(areaResponse.getData().getName());
            }
        }
        else {
            log.error("base-data rpc getById error areaResponse:{}", JSON.toJSONString(areaResponse));
        }
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


    private void preloadAccountOrderCount(Long programId) {
        String userId = BaseParameterHolder.getParameter(USER_ID);
        String code = BaseParameterHolder.getParameter(CODE);
        if (StringUtil.isEmpty(userId) || StringUtil.isEmpty(code)) {
            return;
        }
        Boolean userLogin =
                redisCache.hasKey(RedisKeyBuild.createRedisKey(RedisKeyManage.USER_LOGIN, code, userId));
        if (!userLogin) {
            return;
        }
        BusinessThreadPool.execute(() -> {
            try {
                if (!redisCache.hasKey(RedisKeyBuild.createRedisKey(RedisKeyManage.ACCOUNT_ORDER_COUNT, userId, programId))) {
                    AccountOrderCountDto accountOrderCountDto = new AccountOrderCountDto();
                    accountOrderCountDto.setUserId(Long.parseLong(userId));
                    accountOrderCountDto.setProgramId(programId);
                    ApiResponse<AccountOrderCountVo> apiResponse = orderClient.accountOrderCount(accountOrderCountDto);
                    if (Objects.equals(apiResponse.getCode(), BaseCode.SUCCESS.getCode())) {
                        Optional.ofNullable(apiResponse.getData())
                                .ifPresent(accountOrderCountVo -> redisCache.set(
                                        RedisKeyBuild.createRedisKey(RedisKeyManage.ACCOUNT_ORDER_COUNT, userId, programId),
                                        accountOrderCountVo.getCount(), tokenExpireManager.getTokenExpireTime() + 1,
                                        TimeUnit.MINUTES));
                    }
                    else {
                        log.warn("orderClient.accountOrderCount 调用失败 apiResponse : {}", JSON.toJSONString(apiResponse));
                    }
                }
            }
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

