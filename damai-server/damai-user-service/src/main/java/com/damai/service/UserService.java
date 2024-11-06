package com.damai.service;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.damai.client.BaseDataClient;
import com.damai.common.ApiResponse;
import com.damai.core.RedisKeyManage;
import com.damai.dto.GetChannelDataByCodeDto;
import com.damai.dto.UserAuthenticationDto;
import com.damai.dto.UserExistDto;
import com.damai.dto.UserGetAndTicketUserListDto;
import com.damai.dto.UserIdDto;
import com.damai.dto.UserLoginDto;
import com.damai.dto.UserLogoutDto;
import com.damai.dto.UserMobileDto;
import com.damai.dto.UserRegisterDto;
import com.damai.dto.UserUpdateDto;
import com.damai.dto.UserUpdateEmailDto;
import com.damai.dto.UserUpdateMobileDto;
import com.damai.dto.UserUpdatePasswordDto;
import com.damai.entity.TicketUser;
import com.damai.entity.User;
import com.damai.entity.UserEmail;
import com.damai.entity.UserMobile;
import com.damai.enums.BaseCode;
import com.damai.enums.BusinessStatus;
import com.damai.enums.CompositeCheckType;
import com.damai.exception.DaMaiFrameException;
import com.damai.handler.BloomFilterHandler;
import com.damai.initialize.impl.composite.CompositeContainer;
import com.damai.jwt.TokenUtil;
import com.damai.mapper.TicketUserMapper;
import com.damai.mapper.UserEmailMapper;
import com.damai.mapper.UserMapper;
import com.damai.mapper.UserMobileMapper;
import com.damai.redis.RedisCache;
import com.damai.redis.RedisKeyBuild;
import com.damai.servicelock.LockType;
import com.damai.servicelock.annotion.ServiceLock;
import com.damai.util.StringUtil;
import com.damai.vo.GetChannelDataVo;
import com.damai.vo.TicketUserVo;
import com.damai.vo.UserGetAndTicketUserListVo;
import com.damai.vo.UserLoginVo;
import com.damai.vo.UserVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.damai.constant.Constant.USER_ID;
import static com.damai.core.DistributedLockConstants.REGISTER_USER_LOCK;

@Slf4j
@Service
public class UserService extends ServiceImpl<UserMapper, User> {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserMobileMapper userMobileMapper;

    @Autowired
    private UserEmailMapper userEmailMapper;

    @Autowired
    private UidGenerator uidGenerator;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private TicketUserMapper ticketUserMapper;

    @Autowired
    private BloomFilterHandler bloomFilterHandler;

    @Autowired
    private CompositeContainer compositeContainer;

    @Autowired
    private BaseDataClient baseDataClient;

    @Value("${token.expire.time:40}")
    private Long tokenExpireTime;

    private static final Integer ERROR_COUNT_THRESHOLD = 5;

    /**
     * 用户注册功能
     * 此方法首先执行用户注册的复合检查，然后在用户表和用户手机表中插入新用户的信息，
     * 最后，将用户的手机号添加到布隆过滤器中，以用于快速存在性检查。
     * 使用分布式服务锁确保并发下用户注册的安全性。
     * 通过图形验证码 + 请求数限制 + 布隆过滤器 + 数据库索引 的方式来实现用户注册业务时的缓存穿透以及请求防刷。
     * 1. 开启事务
     * 2. 加分布式锁防止并发问题
     * 3. 使用组合模式进行参数验证
     * 4. 向用户表中添加数据
     * 5. 向用户手机表中添加数据
     * 6. 向布隆过滤器中添加数据
     *
     * @param userRegisterDto 用户注册所需的信息封装。
     * @return 注册成功返回true。
     */
    @Transactional(rollbackFor = Exception.class)
    @ServiceLock(lockType = LockType.Write, name = REGISTER_USER_LOCK, keys = {"#userRegisterDto.mobile"})
    public Boolean register(UserRegisterDto userRegisterDto) {
        // 执行用户注册的复合检查，比如布隆过滤器检查等
        compositeContainer.execute(CompositeCheckType.USER_REGISTER_CHECK.getValue(), userRegisterDto);

        // 记录注册的手机号
        log.info("注册手机号:{}", userRegisterDto.getMobile());

        // 创建用户对象并复制属性
        User user = new User();
        BeanUtils.copyProperties(userRegisterDto, user);
        user.setId(uidGenerator.getUid()); // 生成用户ID

        // 将用户信息插入用户表
        userMapper.insert(user);

        // 创建用户手机对象并设置属性
        UserMobile userMobile = new UserMobile();
        userMobile.setId(uidGenerator.getUid()); // 生成用户手机ID
        userMobile.setUserId(user.getId()); // 设置用户ID
        userMobile.setMobile(userRegisterDto.getMobile()); // 设置手机号

        // 将用户手机信息插入用户手机表
        userMobileMapper.insert(userMobile);

        // 将手机号添加到布隆过滤器中，用于快速存在性检查
        bloomFilterHandler.add(userMobile.getMobile());

        return true;
    }

    /**
     * 检查用户是否存在的接口
     * 此方法通过调用doExist方法来检查给定手机号的用户是否存在。
     * 使用服务锁确保并发下用户存在性检查的安全性。
     *
     * @param userExistDto 包含要检查的手机号的信息封装。
     */
    @ServiceLock(lockType = LockType.Read, name = REGISTER_USER_LOCK, keys = {"#mobile"})
    public void exist(UserExistDto userExistDto) {
        // 调用doExist方法检查用户是否存在
        doExist(userExistDto.getMobile());
    }

    /**
     * 执行用户存在性检查
     * 此方法首先使用布隆过滤器进行快速存在性检查，如果布隆过滤器认为手机号可能已存在，
     * 则进一步查询数据库以确认手机号是否真的已存在。如果用户已存在，抛出异常。
     *
     * @param mobile 要检查的手机号。
     */
    public void doExist(String mobile) {
        // 使用布隆过滤器进行快速存在性检查
        boolean contains = bloomFilterHandler.contains(mobile);

        // 如果布隆过滤器认为手机号可能已存在
        if (contains) {
            // 构建查询条件，查询用户手机表
            LambdaQueryWrapper<UserMobile> queryWrapper = Wrappers.lambdaQuery(UserMobile.class)
                    .eq(UserMobile::getMobile, mobile);

            // 查询用户手机表，获取用户手机信息
            UserMobile userMobile = userMobileMapper.selectOne(queryWrapper);

            // 如果用户手机信息存在，抛出用户已存在的异常
            if (Objects.nonNull(userMobile)) {
                throw new DaMaiFrameException(BaseCode.USER_EXIST);
            }
        }
    }

    /**
     * 登录
     *
     * @param userLoginDto 登录入参，包含登录所需的手机号、邮箱、验证码和密码。
     * @return 用户信息，包括用户ID和生成的Token。
     */
    public UserLoginVo login(UserLoginDto userLoginDto) {
        UserLoginVo userLoginVo = new UserLoginVo();

        // 获取登录参数
        String code = userLoginDto.getCode();
        String mobile = userLoginDto.getMobile();
        String email = userLoginDto.getEmail();
        String password = userLoginDto.getPassword();

        // 检查手机号和邮箱是否都为空，如果都为空则抛出异常
        if (StringUtil.isEmpty(mobile) && StringUtil.isEmpty(email)) {
            throw new DaMaiFrameException(BaseCode.USER_MOBILE_AND_EMAIL_NOT_EXIST);
        }

        // 用户ID，后面根据手机号或者邮箱查询附属路由表得知
        Long userId;

        // 如果提供了手机号
        if (StringUtil.isNotEmpty(mobile)) {
            // 获取手机号错误尝试次数
            String errorCountStr = redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.LOGIN_USER_MOBILE_ERROR, mobile), String.class);

            // 如果错误尝试次数超过阈值，抛出异常
            if (StringUtil.isNotEmpty(errorCountStr) && Integer.parseInt(errorCountStr) >= ERROR_COUNT_THRESHOLD) {
                throw new DaMaiFrameException(BaseCode.MOBILE_ERROR_COUNT_TOO_MANY);
            }

            // 查询用户手机表，获取用户信息
            LambdaQueryWrapper<UserMobile> queryWrapper = Wrappers.lambdaQuery(UserMobile.class)
                    .eq(UserMobile::getMobile, mobile);
            UserMobile userMobile = userMobileMapper.selectOne(queryWrapper);

            // 如果用户不存在，增加错误尝试次数并抛出异常
            if (Objects.isNull(userMobile)) {
                redisCache.incrBy(RedisKeyBuild.createRedisKey(RedisKeyManage.LOGIN_USER_MOBILE_ERROR, mobile), 1);
                redisCache.expire(RedisKeyBuild.createRedisKey(RedisKeyManage.LOGIN_USER_MOBILE_ERROR, mobile), 1, TimeUnit.MINUTES);
                throw new DaMaiFrameException(BaseCode.USER_MOBILE_EMPTY);
            }

            // 获取用户ID
            userId = userMobile.getUserId();
        }
        // 如果提供了邮箱
        else {
            // 获取邮箱错误尝试次数
            String errorCountStr = redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.LOGIN_USER_EMAIL_ERROR, email), String.class);

            // 如果错误尝试次数超过阈值，抛出异常
            if (StringUtil.isNotEmpty(errorCountStr) && Integer.parseInt(errorCountStr) >= ERROR_COUNT_THRESHOLD) {
                throw new DaMaiFrameException(BaseCode.EMAIL_ERROR_COUNT_TOO_MANY);
            }

            // 查询用户邮箱表，获取用户信息
            LambdaQueryWrapper<UserEmail> queryWrapper = Wrappers.lambdaQuery(UserEmail.class)
                    .eq(UserEmail::getEmail, email);
            UserEmail userEmail = userEmailMapper.selectOne(queryWrapper);

            // 如果用户不存在，增加错误尝试次数并抛出异常
            if (Objects.isNull(userEmail)) {
                redisCache.incrBy(RedisKeyBuild.createRedisKey(RedisKeyManage.LOGIN_USER_EMAIL_ERROR, email), 1);
                redisCache.expire(RedisKeyBuild.createRedisKey(RedisKeyManage.LOGIN_USER_EMAIL_ERROR, email), 1, TimeUnit.MINUTES);
                throw new DaMaiFrameException(BaseCode.USER_EMAIL_NOT_EXIST);
            }

            // 获取用户ID
            userId = userEmail.getUserId();
        }

        // 查询用户表，验证用户ID和密码
        LambdaQueryWrapper<User> queryUserWrapper = Wrappers.lambdaQuery(User.class)
                .eq(User::getId, userId).eq(User::getPassword, password);
        User user = userMapper.selectOne(queryUserWrapper);

        // 如果用户不存在或密码错误，抛出异常
        if (Objects.isNull(user)) {
            throw new DaMaiFrameException(BaseCode.NAME_PASSWORD_ERROR);
        }

        // 将用户信息存入Redis缓存，设置过期时间
        RedisKeyBuild redisKey = RedisKeyBuild.createRedisKey(RedisKeyManage.USER_LOGIN, code, userId);
        redisCache.set(redisKey, user, tokenExpireTime, TimeUnit.MINUTES);

        // 根据渠道码获取渠道数据信息
        GetChannelDataVo channelDataVo = getChannelDataByCode(code);
        // 从渠道数据中获取密钥用于生成令牌
        String tokenSecret = channelDataVo.getTokenSecret();
        // 使用用户ID和密钥生成安全令牌
        String token = createToken(userId, tokenSecret);

        // 设置返回的用户ID和Token
        userLoginVo.setUserId(userId);
        userLoginVo.setToken(token);

        // 返回用户登录信息
        return userLoginVo;
    }

    /**
     * 通过Redis获取渠道数据
     *
     * @param code 渠道标识码，用于在Redis中查找对应的渠道数据
     * @return GetChannelDataVo 返回获取到的渠道数据对象，如果找不到则返回null
     */
    private GetChannelDataVo getChannelDataByRedis(String code) {
        return redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.CHANNEL_DATA, code), GetChannelDataVo.class);
    }

    /**
     * 通过客户端接口获取渠道数据
     * 当Redis中没有缓存数据时调用此方法，从远程服务获取渠道数据
     *
     * @param code 渠道标识码，用于请求特定的渠道数据
     * @return GetChannelDataVo 返回获取到的渠道数据对象
     * @throws DaMaiFrameException 如果请求失败或找不到对应的渠道数据，则抛出此异常
     */
    private GetChannelDataVo getChannelDataByClient(String code) {
        // 创建一个GetChannelDataByCodeDto对象，用于后续查询渠道数据
        GetChannelDataByCodeDto getChannelDataByCodeDto = new GetChannelDataByCodeDto();
        // 设置查询条件，即渠道代码
        getChannelDataByCodeDto.setCode(code);
        // 通过RPC调用baseDataClient的getByCode方法，根据渠道代码获取渠道数据
        ApiResponse<GetChannelDataVo> getChannelDataApiResponse = baseDataClient.getByCode(getChannelDataByCodeDto);
        // 检查API响应的状态码是否表示成功
        if (Objects.equals(getChannelDataApiResponse.getCode(), BaseCode.SUCCESS.getCode())) {
            // 如果成功，返回渠道数据
            return getChannelDataApiResponse.getData();
        }
        // 如果响应状态码不表示成功，抛出异常提示未找到ChannelData
        throw new DaMaiFrameException("没有找到ChannelData");
    }

    /**
     * 创建用户令牌
     * 令牌包括内容：令牌ID、用户ID
     *
     * @param userId      用户ID，用于标识令牌的拥有者
     * @param tokenSecret 令牌的密钥，用于保证令牌的安全性
     * @return 返回生成的用户令牌字符串
     */
    public String createToken(Long userId, String tokenSecret) {
        // 创建需要在令牌中携带的信息Map
        Map<String, Object> map = new HashMap<>(4);

        // 放入用户ID
        map.put(USER_ID, userId);

        // 将map转换为json字符串
        String info = JSON.toJSONString(map);

        // 生成令牌ID
        String id = String.valueOf(uidGenerator.getUid());

        // 设置过期时间
        long expiration = tokenExpireTime * 60 * 1000;

        // 生成令牌并返回
        return TokenUtil.createToken(id, info, expiration, tokenSecret);
    }

    /**
     * 用户退出登录
     *
     * @param userLogoutDto 用户退出登录的请求参数，包括 code 和 token
     * @return
     */
    public Boolean logout(UserLogoutDto userLogoutDto) {
        // 从入参中解析出用户信息
        String token = userLogoutDto.getToken();
        String tokenSecret = getChannelDataByCode(userLogoutDto.getCode()).getTokenSecret();
        String userStr = TokenUtil.parseToken(token, tokenSecret);

        // 不存在用户信息则报异常
        if (StringUtil.isEmpty(userStr)) {
            throw new DaMaiFrameException(BaseCode.USER_EMPTY);
        }

        // 从Redis中删除用户登录信息
        String userId = JSONObject.parseObject(userStr).getString("userId");
        redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.USER_LOGIN, userLogoutDto.getCode(), userId));

        // 返回成功
        return true;
    }

    /**
     * 根据渠道码获取渠道数据信息
     * 首先尝试从Redis中获取数据，如果未找到，则通过客户端请求获取
     * 这种方式旨在提高数据获取效率，减少对外部服务的依赖
     *
     * @param code 渠道码，用于标识特定的渠道
     * @return GetChannelDataVo 渠道数据信息的封装对象，如果找不到对应数据，则返回null
     */
    public GetChannelDataVo getChannelDataByCode(String code) {
        // 尝试从Redis中获取渠道数据信息
        GetChannelDataVo channelDataVo = getChannelDataByRedis(code);
        if (Objects.isNull(channelDataVo)) {
            // 如果Redis中不存在该渠道数据，则通过客户端请求获取
            channelDataVo = getChannelDataByClient(code);
        }
        return channelDataVo;
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(UserUpdateDto userUpdateDto) {
        User user = userMapper.selectById(userUpdateDto.getId());
        if (Objects.isNull(user)) {
            throw new DaMaiFrameException(BaseCode.USER_EMPTY);
        }
        User updateUser = new User();
        BeanUtil.copyProperties(userUpdateDto, updateUser);
        userMapper.updateById(updateUser);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(UserUpdatePasswordDto userUpdatePasswordDto) {
        User user = userMapper.selectById(userUpdatePasswordDto.getId());
        if (Objects.isNull(user)) {
            throw new DaMaiFrameException(BaseCode.USER_EMPTY);
        }
        User updateUser = new User();
        BeanUtil.copyProperties(userUpdatePasswordDto, updateUser);
        userMapper.updateById(updateUser);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateEmail(UserUpdateEmailDto userUpdateEmailDto) {
        User user = userMapper.selectById(userUpdateEmailDto.getId());
        if (Objects.isNull(user)) {
            throw new DaMaiFrameException(BaseCode.USER_EMPTY);
        }
        User updateUser = new User();
        BeanUtil.copyProperties(userUpdateEmailDto, updateUser);
        updateUser.setEmailStatus(BusinessStatus.YES.getCode());
        userMapper.updateById(updateUser);

        String oldEmail = user.getEmail();
        LambdaQueryWrapper<UserEmail> userEmailLambdaQueryWrapper = Wrappers.lambdaQuery(UserEmail.class)
                .eq(UserEmail::getEmail, userUpdateEmailDto.getEmail());
        UserEmail userEmail = userEmailMapper.selectOne(userEmailLambdaQueryWrapper);
        if (Objects.isNull(userEmail)) {
            userEmail = new UserEmail();
            userEmail.setId(uidGenerator.getUid());
            userEmail.setUserId(user.getId());
            userEmail.setEmail(userUpdateEmailDto.getEmail());
            userEmailMapper.insert(userEmail);
        }
        else {
            LambdaUpdateWrapper<UserEmail> userEmailLambdaUpdateWrapper = Wrappers.lambdaUpdate(UserEmail.class)
                    .eq(UserEmail::getEmail, oldEmail);
            UserEmail updateUserEmail = new UserEmail();
            updateUserEmail.setEmail(userUpdateEmailDto.getEmail());
            userEmailMapper.update(updateUserEmail, userEmailLambdaUpdateWrapper);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateMobile(UserUpdateMobileDto userUpdateMobileDto) {
        User user = userMapper.selectById(userUpdateMobileDto.getId());
        if (Objects.isNull(user)) {
            throw new DaMaiFrameException(BaseCode.USER_EMPTY);
        }
        String oldMobile = user.getMobile();
        User updateUser = new User();
        BeanUtil.copyProperties(userUpdateMobileDto, updateUser);
        userMapper.updateById(updateUser);
        LambdaQueryWrapper<UserMobile> userMobileLambdaQueryWrapper = Wrappers.lambdaQuery(UserMobile.class)
                .eq(UserMobile::getMobile, userUpdateMobileDto.getMobile());
        UserMobile userMobile = userMobileMapper.selectOne(userMobileLambdaQueryWrapper);
        if (Objects.isNull(userMobile)) {
            userMobile = new UserMobile();
            userMobile.setId(uidGenerator.getUid());
            userMobile.setUserId(user.getId());
            userMobile.setMobile(userUpdateMobileDto.getMobile());
            userMobileMapper.insert(userMobile);
        }
        else {
            LambdaUpdateWrapper<UserMobile> userMobileLambdaUpdateWrapper = Wrappers.lambdaUpdate(UserMobile.class)
                    .eq(UserMobile::getMobile, oldMobile);
            UserMobile updateUserMobile = new UserMobile();
            updateUserMobile.setMobile(userUpdateMobileDto.getMobile());
            userMobileMapper.update(updateUserMobile, userMobileLambdaUpdateWrapper);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void authentication(UserAuthenticationDto userAuthenticationDto) {
        User user = userMapper.selectById(userAuthenticationDto.getId());
        if (Objects.isNull(user)) {
            throw new DaMaiFrameException(BaseCode.USER_EMPTY);
        }
        if (Objects.equals(user.getRelAuthenticationStatus(), BusinessStatus.YES.getCode())) {
            throw new DaMaiFrameException(BaseCode.USER_AUTHENTICATION);
        }
        User updateUser = new User();
        updateUser.setId(user.getId());
        updateUser.setRelName(userAuthenticationDto.getRelName());
        updateUser.setIdNumber(userAuthenticationDto.getIdNumber());
        updateUser.setRelAuthenticationStatus(BusinessStatus.YES.getCode());
        userMapper.updateById(updateUser);
    }

    public UserVo getByMobile(UserMobileDto userMobileDto) {
        LambdaQueryWrapper<UserMobile> queryWrapper = Wrappers.lambdaQuery(UserMobile.class)
                .eq(UserMobile::getMobile, userMobileDto.getMobile());
        UserMobile userMobile = userMobileMapper.selectOne(queryWrapper);
        if (Objects.isNull(userMobile)) {
            throw new DaMaiFrameException(BaseCode.USER_MOBILE_EMPTY);
        }
        User user = userMapper.selectById(userMobile.getUserId());
        if (Objects.isNull(user)) {
            throw new DaMaiFrameException(BaseCode.USER_EMPTY);
        }
        UserVo userVo = new UserVo();
        BeanUtil.copyProperties(user, userVo);
        userVo.setMobile(userMobile.getMobile());
        return userVo;
    }

    public UserVo getById(UserIdDto userIdDto) {
        User user = userMapper.selectById(userIdDto.getId());
        if (Objects.isNull(user)) {
            throw new DaMaiFrameException(BaseCode.USER_EMPTY);
        }
        UserVo userVo = new UserVo();
        BeanUtil.copyProperties(user, userVo);
        return userVo;
    }

    public UserGetAndTicketUserListVo getUserAndTicketUserList(final UserGetAndTicketUserListDto userGetAndTicketUserListDto) {
        UserIdDto userIdDto = new UserIdDto();
        userIdDto.setId(userGetAndTicketUserListDto.getUserId());
        UserVo userVo = getById(userIdDto);

        LambdaQueryWrapper<TicketUser> ticketUserLambdaQueryWrapper = Wrappers.lambdaQuery(TicketUser.class)
                .eq(TicketUser::getUserId, userGetAndTicketUserListDto.getUserId());
        List<TicketUser> ticketUserList = ticketUserMapper.selectList(ticketUserLambdaQueryWrapper);
        List<TicketUserVo> ticketUserVoList = BeanUtil.copyToList(ticketUserList, TicketUserVo.class);

        UserGetAndTicketUserListVo userGetAndTicketUserListVo = new UserGetAndTicketUserListVo();
        userGetAndTicketUserListVo.setUserVo(userVo);
        userGetAndTicketUserListVo.setTicketUserVoList(ticketUserVoList);
        return userGetAndTicketUserListVo;
    }

    public List<String> getAllMobile() {
        QueryWrapper<User> lambdaQueryWrapper = Wrappers.emptyWrapper();
        List<User> users = userMapper.selectList(lambdaQueryWrapper);
        return users.stream().map(User::getMobile).collect(Collectors.toList());
    }
}
