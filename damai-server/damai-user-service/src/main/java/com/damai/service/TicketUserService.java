package com.damai.service;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.damai.core.RedisKeyManage;
import com.damai.dto.TicketUserDto;
import com.damai.dto.TicketUserIdDto;
import com.damai.dto.TicketUserListDto;
import com.damai.entity.TicketUser;
import com.damai.entity.User;
import com.damai.enums.BaseCode;
import com.damai.exception.DaMaiFrameException;
import com.damai.mapper.TicketUserMapper;
import com.damai.mapper.UserMapper;
import com.damai.redis.RedisCache;
import com.damai.redis.RedisKeyBuild;
import com.damai.vo.TicketUserVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 这里是票务用户服务实现类，其中查询列表应用了缓存，一致性策略为旁路缓存
 */
@Service
public class TicketUserService extends ServiceImpl<TicketUserMapper, TicketUser> {

	@Autowired
	private TicketUserMapper ticketUserMapper;

	@Autowired
	private UserMapper userMapper;

	@Autowired
	private UidGenerator uidGenerator;

	@Autowired
	private RedisCache redisCache;

	/**
	 * 查询票务用户列表
	 * 首先尝试从缓存中获取数据，如果缓存为空，则从数据库中查询，并返回结果
	 *
	 * @param ticketUserListDto 包含查询条件的DTO对象
	 * @return 票务用户信息列表
	 */
	public List<TicketUserVo> list(TicketUserListDto ticketUserListDto) {
		// 先从缓存中查询
		List<TicketUserVo> ticketUserVoList = redisCache.getValueIsList(RedisKeyBuild.createRedisKey(
				RedisKeyManage.TICKET_USER_LIST, ticketUserListDto.getUserId()), TicketUserVo.class);
		if (CollectionUtil.isNotEmpty(ticketUserVoList)) {
			return ticketUserVoList;
		}

		// 如果缓存为空，则从数据库查询
		LambdaQueryWrapper<TicketUser> ticketUserLambdaQueryWrapper = Wrappers.lambdaQuery(TicketUser.class)
				.eq(TicketUser::getUserId, ticketUserListDto.getUserId());
		List<TicketUser> ticketUsers = ticketUserMapper.selectList(ticketUserLambdaQueryWrapper);
		return BeanUtil.copyToList(ticketUsers, TicketUserVo.class);
	}

	/**
	 * 添加票务用户信息
	 * 首先检查用户是否存在，然后检查票务用户是否已存在，最后插入新的票务用户信息
	 *
	 * @param ticketUserDto 包含票务用户信息的DTO对象
	 */
	@Transactional(rollbackFor = Exception.class)
	public void add(TicketUserDto ticketUserDto) {
		// 检查用户是否存在
		User user = userMapper.selectById(ticketUserDto.getUserId());
		if (Objects.isNull(user)) {
			throw new DaMaiFrameException(BaseCode.USER_EMPTY);
		}

		// 检查票务用户是否已存在
		LambdaQueryWrapper<TicketUser> ticketUserLambdaQueryWrapper = Wrappers.lambdaQuery(TicketUser.class)
				.eq(TicketUser::getUserId, ticketUserDto.getUserId())
				.eq(TicketUser::getIdType, ticketUserDto.getIdType())
				.eq(TicketUser::getIdNumber, ticketUserDto.getIdNumber());
		TicketUser ticketUser = ticketUserMapper.selectOne(ticketUserLambdaQueryWrapper);
		if (Objects.nonNull(ticketUser)) {
			throw new DaMaiFrameException(BaseCode.TICKET_USER_EXIST);
		}

		// 插入新的票务用户信息
		TicketUser addTicketUser = new TicketUser();
		BeanUtil.copyProperties(ticketUserDto, addTicketUser);
		addTicketUser.setId(uidGenerator.getUid());
		ticketUserMapper.insert(addTicketUser);

		// 清除票务用户列表缓存
		delTicketUserVoListCache(String.valueOf(ticketUserDto.getUserId()));
	}

	/**
	 * 删除票务用户信息
	 * 首先检查票务用户是否存在，然后删除，并清除缓存
	 *
	 * @param ticketUserIdDto 包含票务用户ID的DTO对象
	 */
	@Transactional(rollbackFor = Exception.class)
	public void delete(TicketUserIdDto ticketUserIdDto) {
		// 检查票务用户是否存在
		TicketUser ticketUser = ticketUserMapper.selectById(ticketUserIdDto.getId());
		if (Objects.isNull(ticketUser)) {
			throw new DaMaiFrameException(BaseCode.TICKET_USER_EMPTY);
		}

		// 删除票务用户信息
		ticketUserMapper.deleteById(ticketUserIdDto.getId());

		// 清除票务用户列表缓存
		delTicketUserVoListCache(String.valueOf(ticketUser.getUserId()));
	}

	/**
	 * 清除票务用户列表缓存
	 *
	 * @param userId 用户ID，用于构建缓存键
	 */
	public void delTicketUserVoListCache(String userId) {
		redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.TICKET_USER_LIST, userId));
	}
}
