package com.damai.service;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.damai.core.RedisKeyManage;
import com.damai.dto.ProgramShowTimeAddDto;
import com.damai.entity.Program;
import com.damai.entity.ProgramGroup;
import com.damai.entity.ProgramShowTime;
import com.damai.enums.BaseCode;
import com.damai.exception.DaMaiFrameException;
import com.damai.mapper.ProgramGroupMapper;
import com.damai.mapper.ProgramMapper;
import com.damai.mapper.ProgramShowTimeMapper;
import com.damai.redis.RedisCache;
import com.damai.redis.RedisKeyBuild;
import com.damai.service.cache.local.LocalCacheProgramShowTime;
import com.damai.servicelock.LockType;
import com.damai.servicelock.annotion.ServiceLock;
import com.damai.util.DateUtils;
import com.damai.util.ServiceLockTool;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.damai.core.DistributedLockConstants.GET_PROGRAM_SHOW_TIME_LOCK;
import static com.damai.core.DistributedLockConstants.PROGRAM_SHOW_TIME_LOCK;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 节目演出时间 service
 * @author: 阿星不是程序员
 **/
@Service
public class ProgramShowTimeService extends ServiceImpl<ProgramShowTimeMapper, ProgramShowTime> {

	@Autowired
	private UidGenerator uidGenerator;

	@Autowired
	private RedisCache redisCache;

	@Autowired
	private ProgramMapper programMapper;

	@Autowired
	private ProgramShowTimeMapper programShowTimeMapper;

	@Autowired
	private ProgramGroupMapper programGroupMapper;

	@Autowired
	private ServiceLockTool serviceLockTool;

	@Autowired
	private LocalCacheProgramShowTime localCacheProgramShowTime;


	@Transactional(rollbackFor = Exception.class)
	public Long add(ProgramShowTimeAddDto programShowTimeAddDto) {
		ProgramShowTime programShowTime = new ProgramShowTime();
		BeanUtil.copyProperties(programShowTimeAddDto, programShowTime);
		programShowTime.setId(uidGenerator.getUid());
		programShowTimeMapper.insert(programShowTime);
		return programShowTime.getId();
	}

	public ProgramShowTime selectProgramShowTimeByProgramIdMultipleCache(Long programId) {
		return localCacheProgramShowTime.getCache(RedisKeyBuild.createRedisKey
						(RedisKeyManage.PROGRAM_SHOW_TIME, programId).getRelKey(),
				key -> selectProgramShowTimeByProgramId(programId));
	}

	public ProgramShowTime simpleSelectProgramShowTimeByProgramIdMultipleCache(Long programId) {
		ProgramShowTime programShowTimeCache = localCacheProgramShowTime.getCache(RedisKeyBuild.createRedisKey(
				RedisKeyManage.PROGRAM_SHOW_TIME, programId).getRelKey());
		if (Objects.nonNull(programShowTimeCache)) {
			return programShowTimeCache;
		}
		return redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SHOW_TIME,
				programId), ProgramShowTime.class);
	}

	/**
	 * 根据节目ID选择节目播出时间
	 * 此方法使用了服务端锁来避免并发情况下可能出现的脏读问题
	 * 它首先尝试从Redis缓存中获取节目播出时间信息，如果未找到，则加锁后从数据库中查询，并将结果缓存
	 *
	 * @param programId 节目ID，用于查询节目播出时间
	 * @return ProgramShowTime 节目播出时间对象
	 * @throws DaMaiFrameException 如果未找到对应的节目播出时间信息，则抛出此异常
	 */
	@ServiceLock(lockType = LockType.Read, name = PROGRAM_SHOW_TIME_LOCK, keys = {"#programId"})
	public ProgramShowTime selectProgramShowTimeByProgramId(Long programId) {

		// 构建Redis存储键
		RedisKeyBuild redisKey = RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_SHOW_TIME, programId);

		// 尝试从Redis缓存中获取节目播出时间信息
		ProgramShowTime programShowTime = redisCache.get(redisKey, ProgramShowTime.class);

		// 如果缓存中存在节目播出时间信息，则直接返回
		if (Objects.nonNull(programShowTime)) {
			return programShowTime;
		}

		// 获取锁
		RLock lock = serviceLockTool.getLock(LockType.Reentrant, GET_PROGRAM_SHOW_TIME_LOCK,
				new String[]{String.valueOf(programId)});
		lock.lock();
		try {
			// 再次尝试从Redis缓存中获取节目播出时间信息，以避免并发情况下重复查询数据库
			programShowTime = redisCache.get(redisKey, ProgramShowTime.class);
			// 如果缓存中不存在节目播出时间信息，则从数据库中查询
			if (Objects.isNull(programShowTime)) {
				LambdaQueryWrapper<ProgramShowTime> programShowTimeLambdaQueryWrapper =
						Wrappers.lambdaQuery(ProgramShowTime.class).eq(ProgramShowTime::getProgramId, programId);
				programShowTime = Optional.ofNullable(programShowTimeMapper.selectOne(programShowTimeLambdaQueryWrapper))
						.orElseThrow(() -> new DaMaiFrameException(BaseCode.PROGRAM_SHOW_TIME_NOT_EXIST));
				// 将查询到的节目播出时间信息存入Redis缓存
				redisCache.set(redisKey, programShowTime
						, DateUtils.countBetweenSecond(DateUtils.now(), programShowTime.getShowTime()), TimeUnit.SECONDS);
			}
			return programShowTime;
		}
		finally {
			// 释放锁
			lock.unlock();
		}
	}

	@Transactional(rollbackFor = Exception.class)
	public Set<Long> renewal() {
		Set<Long> programIdSet = new HashSet<>();
		LambdaQueryWrapper<ProgramShowTime> programShowTimeLambdaQueryWrapper =
				Wrappers.lambdaQuery(ProgramShowTime.class).
						le(ProgramShowTime::getShowTime, DateUtils.addDay(DateUtils.now(), 2));
		List<ProgramShowTime> programShowTimes = programShowTimeMapper.selectList(programShowTimeLambdaQueryWrapper);

		List<ProgramShowTime> newProgramShowTimes = new ArrayList<>(programShowTimes.size());

		for (ProgramShowTime programShowTime : programShowTimes) {
			programIdSet.add(programShowTime.getProgramId());
			Date oldShowTime = programShowTime.getShowTime();
			Date newShowTime = DateUtils.addMonth(oldShowTime, 1);
			Date nowDateTime = DateUtils.now();
			while (newShowTime.before(nowDateTime)) {
				newShowTime = DateUtils.addMonth(newShowTime, 1);
			}
			Date newShowDayTime = DateUtils.parseDateTime(DateUtils.formatDate(newShowTime) + " 00:00:00");
			ProgramShowTime updateProgramShowTime = new ProgramShowTime();
			updateProgramShowTime.setShowTime(newShowTime);
			updateProgramShowTime.setShowDayTime(newShowDayTime);
			updateProgramShowTime.setShowWeekTime(DateUtils.getWeekStr(newShowTime));
			LambdaUpdateWrapper<ProgramShowTime> programShowTimeLambdaUpdateWrapper =
					Wrappers.lambdaUpdate(ProgramShowTime.class)
							.eq(ProgramShowTime::getProgramId, programShowTime.getProgramId())
							.eq(ProgramShowTime::getId, programShowTime.getId());

			programShowTimeMapper.update(updateProgramShowTime, programShowTimeLambdaUpdateWrapper);

			ProgramShowTime newProgramShowTime = new ProgramShowTime();
			newProgramShowTime.setProgramId(programShowTime.getProgramId());
			newProgramShowTime.setShowTime(newShowTime);
			newProgramShowTimes.add(newProgramShowTime);
		}
		Map<Long, Date> programGroupMap = new HashMap<>(newProgramShowTimes.size());
		for (ProgramShowTime newProgramShowTime : newProgramShowTimes) {
			Program program = programMapper.selectById(newProgramShowTime.getProgramId());
			if (Objects.isNull(program)) {
				continue;
			}
			Long programGroupId = program.getProgramGroupId();
			Date showTime = programGroupMap.get(programGroupId);
			if (Objects.isNull(showTime)) {
				programGroupMap.put(programGroupId, newProgramShowTime.getShowTime());
			}
			else {
				if (DateUtil.compare(newProgramShowTime.getShowTime(), showTime) < 0) {
					programGroupMap.put(programGroupId, newProgramShowTime.getShowTime());
				}
			}
		}
		if (CollectionUtil.isNotEmpty(programGroupMap)) {
			programGroupMap.forEach((k, v) -> {
				ProgramGroup programGroup = new ProgramGroup();
				programGroup.setRecentShowTime(v);

				LambdaUpdateWrapper<ProgramGroup> programGroupLambdaUpdateWrapper =
						Wrappers.lambdaUpdate(ProgramGroup.class)
								.eq(ProgramGroup::getId, k);
				programGroupMapper.update(programGroup, programGroupLambdaUpdateWrapper);
			});
		}

		return programIdSet;
	}
}
