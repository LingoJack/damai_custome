package com.damai.service;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.damai.core.RedisKeyManage;
import com.damai.dto.TicketCategoryAddDto;
import com.damai.dto.TicketCategoryDto;
import com.damai.entity.TicketCategory;
import com.damai.mapper.TicketCategoryMapper;
import com.damai.redis.RedisCache;
import com.damai.redis.RedisKeyBuild;
import com.damai.service.cache.local.LocalCacheTicketCategory;
import com.damai.servicelock.LockType;
import com.damai.servicelock.annotion.ServiceLock;
import com.damai.util.DateUtils;
import com.damai.util.ServiceLockTool;
import com.damai.vo.TicketCategoryDetailVo;
import com.damai.vo.TicketCategoryVo;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.damai.core.DistributedLockConstants.GET_REMAIN_NUMBER_LOCK;
import static com.damai.core.DistributedLockConstants.GET_TICKET_CATEGORY_LOCK;
import static com.damai.core.DistributedLockConstants.REMAIN_NUMBER_LOCK;
import static com.damai.core.DistributedLockConstants.TICKET_CATEGORY_LOCK;

/**
 * 票档 service
 **/
@Slf4j
@Service
public class TicketCategoryService extends ServiceImpl<TicketCategoryMapper, TicketCategory> {

    @Autowired
    private UidGenerator uidGenerator;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private TicketCategoryMapper ticketCategoryMapper;

    @Autowired
    private ServiceLockTool serviceLockTool;

    @Autowired
    private LocalCacheTicketCategory localCacheTicketCategory;

    /**
     * 添加票档
     *
     * @param ticketCategoryAddDto 票档添加DTO
     * @return 添加后的票档ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long add(TicketCategoryAddDto ticketCategoryAddDto) {
        TicketCategory ticketCategory = new TicketCategory();
        BeanUtil.copyProperties(ticketCategoryAddDto, ticketCategory);
        ticketCategory.setId(uidGenerator.getUid());
        ticketCategoryMapper.insert(ticketCategory);
        return ticketCategory.getId();
    }

    /**
     * 通过节目ID查询票档列表，使用多级缓存
     *
     * @param programId 节目ID
     * @param showTime  演出时间
     * @return 票档列表
     */
    public List<TicketCategoryVo> selectTicketCategoryListByProgramIdMultipleCache(Long programId, Date showTime) {
        return localCacheTicketCategory.getCache(programId, key -> {
            long expireTime = DateUtils.countBetweenSecond(DateUtils.now(), showTime);
            return selectTicketCategoryListByProgramId(programId, expireTime, TimeUnit.SECONDS);
        });
    }

    /**
     * 通过节目ID查询票档列表，带有服务锁
     *
     * @param programId  节目ID
     * @param expireTime 过期时间
     * @param timeUnit   时间单位
     * @return 票档列表
     */
    @ServiceLock(lockType = LockType.Read, name = TICKET_CATEGORY_LOCK, keys = {"#programId"})
    public List<TicketCategoryVo> selectTicketCategoryListByProgramId(Long programId, Long expireTime, TimeUnit timeUnit) {
        // 根据programId查询Redis
        RedisKeyBuild redisKey = RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_TICKET_CATEGORY_LIST, programId);

        // 获取Redis中的查询结果
        List<TicketCategoryVo> ticketCategoryVoList = redisCache.getValueIsList(redisKey, TicketCategoryVo.class);

        // 若redis中存在数据，直接返回
        if (CollectionUtil.isNotEmpty(ticketCategoryVoList)) {
            return ticketCategoryVoList;
        }

        // 若redis中不存在数据，则
        RLock lock = serviceLockTool.getLock(LockType.Reentrant, GET_TICKET_CATEGORY_LOCK, new String[]{String.valueOf(programId)});
        lock.lock();
        try {
            return redisCache.getValueIsList(
                    redisKey,
                    TicketCategoryVo.class,
                    () -> {
                        LambdaQueryWrapper<TicketCategory> ticketCategoryLambdaQueryWrapper =
                                Wrappers.lambdaQuery(TicketCategory.class).eq(TicketCategory::getProgramId, programId);
                        List<TicketCategory> ticketCategoryList =
                                ticketCategoryMapper.selectList(ticketCategoryLambdaQueryWrapper);
                        return ticketCategoryList.stream().map(ticketCategory -> {
                            //这里需要把remainNumber设置为null，因为准确的剩余票数是在Redis中更新的，数据库会有延迟
                            // 故此处直接将剩余票数置空，不在前端展示
                            ticketCategory.setRemainNumber(null);
                            TicketCategoryVo ticketCategoryVo = new TicketCategoryVo();
                            BeanUtil.copyProperties(ticketCategory, ticketCategoryVo);
                            return ticketCategoryVo;
                        }).collect(Collectors.toList());
                    }, expireTime, timeUnit);
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * 获取Redis中的剩余票数，带有服务锁
     *
     * @param programId        节目ID
     * @param ticketCategoryId 票档ID
     * @return 剩余票数映射
     */
    @ServiceLock(lockType = LockType.Read, name = REMAIN_NUMBER_LOCK, keys = {"#programId", "#ticketCategoryId"})
    public Map<String, Long> getRedisRemainNumberResolution(Long programId, Long ticketCategoryId) {
        // 构建redis存储键
        RedisKeyBuild redisKey = RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_TICKET_REMAIN_NUMBER_HASH_RESOLUTION, programId, ticketCategoryId);

        // 尝试从redis中获取剩余票数
        Map<String, Long> ticketCategoryRemainNumber = redisCache.getAllMapForHash(redisKey, Long.class);

        // 若redis中存在数据，直接返回
        if (CollectionUtil.isNotEmpty(ticketCategoryRemainNumber)) {
            return ticketCategoryRemainNumber;
        }

        // 若redis中不存在数据，则需要由一个线程去获取锁，然后从数据库中查询数据，放入缓存
        RLock lock = serviceLockTool.getLock(LockType.Reentrant, GET_REMAIN_NUMBER_LOCK,
                new String[]{String.valueOf(programId), String.valueOf(ticketCategoryId)});

        // 加锁
        lock.lock();
        try {
            // 尝试从缓存中获取数据，这里之所以要再次获取数据是为了保证只有一条线程去读取数据库并放入缓存
            // 这是双重检测锁思想的一种运用
            ticketCategoryRemainNumber = redisCache.getAllMapForHash(redisKey, Long.class);

            // 若缓存中已经有数据，则直接返回
            if (CollectionUtil.isNotEmpty(ticketCategoryRemainNumber)) {
                return ticketCategoryRemainNumber;
            }

            // 若缓存中没有数据，则查询数据库，并置入缓存
            LambdaQueryWrapper<TicketCategory> ticketCategoryLambdaQueryWrapper = Wrappers.lambdaQuery(TicketCategory.class)
                    .eq(TicketCategory::getProgramId, programId).eq(TicketCategory::getId, ticketCategoryId);
            List<TicketCategory> ticketCategoryList = ticketCategoryMapper.selectList(ticketCategoryLambdaQueryWrapper);

            // 根据从数据库中查询的结果构建要放入缓存中的Map，以id为票档ID为键，剩余票数为值
            Map<String, Long> map = ticketCategoryList.stream()
                    .collect(Collectors.toMap(t -> String.valueOf(t.getId()), TicketCategory::getRemainNumber, (v1, v2) -> v2));
            redisCache.putHash(redisKey, map);
            return map;
        }
        finally {
            // 释放锁
            lock.unlock();
        }
    }

    /**
     * 查询票档详情
     *
     * @param ticketCategoryDto 票档DTO
     * @return 票档详情VO
     */
    public TicketCategoryDetailVo detail(TicketCategoryDto ticketCategoryDto) {
        TicketCategory ticketCategory = ticketCategoryMapper.selectById(ticketCategoryDto.getId());
        TicketCategoryDetailVo ticketCategoryDetailVo = new TicketCategoryDetailVo();
        BeanUtil.copyProperties(ticketCategory, ticketCategoryDetailVo);
        return ticketCategoryDetailVo;
    }
}

