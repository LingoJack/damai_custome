package com.damai.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.damai.core.RedisKeyManage;
import com.damai.dto.ParentProgramCategoryDto;
import com.damai.dto.ProgramCategoryAddDto;
import com.damai.dto.ProgramCategoryDto;
import com.damai.entity.ProgramCategory;
import com.damai.mapper.ProgramCategoryMapper;
import com.damai.redis.RedisCache;
import com.damai.redis.RedisKeyBuild;
import com.damai.servicelock.LockType;
import com.damai.servicelock.annotion.ServiceLock;
import com.damai.vo.ProgramCategoryVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.damai.core.DistributedLockConstants.PROGRAM_CATEGORY_LOCK;

/**
 * 节目类型 service
 **/
@Service
public class ProgramCategoryService extends ServiceImpl<ProgramCategoryMapper, ProgramCategory> {

    @Autowired
    private ProgramCategoryMapper programCategoryMapper;

    @Autowired
    private UidGenerator uidGenerator;

    @Autowired
    private RedisCache redisCache;

    /**
     * 查询所有节目类型
     *
     * @return 节目类型列表
     */
    public List<ProgramCategoryVo> selectAll() {
        QueryWrapper<ProgramCategory> lambdaQueryWrapper = Wrappers.emptyWrapper();
        List<ProgramCategory> programCategoryList = programCategoryMapper.selectList(lambdaQueryWrapper);
        return BeanUtil.copyToList(programCategoryList, ProgramCategoryVo.class);
    }

    /**
     * 根据类型查询节目类型
     *
     * @param programCategoryDto 节目类型 DTO
     * @return 对应类型的节目类型列表
     */
    public List<ProgramCategoryVo> selectByType(ProgramCategoryDto programCategoryDto) {
        LambdaQueryWrapper<ProgramCategory> lambdaQueryWrapper = Wrappers.lambdaQuery(ProgramCategory.class)
                .eq(ProgramCategory::getType, programCategoryDto.getType());
        List<ProgramCategory> programCategories = programCategoryMapper.selectList(lambdaQueryWrapper);
        return BeanUtil.copyToList(programCategories, ProgramCategoryVo.class);
    }

    /**
     * 根据父节目类型 ID 查询节目类型
     *
     * @param parentProgramCategoryDto 父节目类型 DTO
     * @return 对应父类型的节目类型列表
     */
    public List<ProgramCategoryVo> selectByParentProgramCategoryId(ParentProgramCategoryDto parentProgramCategoryDto) {
        LambdaQueryWrapper<ProgramCategory> lambdaQueryWrapper = Wrappers.lambdaQuery(ProgramCategory.class)
                .eq(ProgramCategory::getParentId, parentProgramCategoryDto.getParentProgramCategoryId());
        List<ProgramCategory> programCategories = programCategoryMapper.selectList(lambdaQueryWrapper);
        return BeanUtil.copyToList(programCategories, ProgramCategoryVo.class);
    }

    /**
     * 批量保存节目类型
     *
     * @param programCategoryAddDtoList 节目类型添加 DTO 列表
     */
    @Transactional(rollbackFor = Exception.class)
    @ServiceLock(lockType = LockType.Write, name = PROGRAM_CATEGORY_LOCK, keys = {"all"})
    public void saveBatch(final List<ProgramCategoryAddDto> programCategoryAddDtoList) {
        // 构建需要保存的实体列表，为每一项设置ID
        List<ProgramCategory> programCategoryList = programCategoryAddDtoList.stream().map(programCategoryAddDto -> {
            ProgramCategory programCategory = new ProgramCategory();
            BeanUtil.copyProperties(programCategoryAddDto, programCategory);
            programCategory.setId(uidGenerator.getUid());
            return programCategory;
        }).collect(Collectors.toList());

        // 如果需要保存的实体列表不为空，则批量保存
        if (CollectionUtil.isNotEmpty(programCategoryList)) {
            this.saveBatch(programCategoryList);
            Map<String, ProgramCategory> programCategoryMap = programCategoryList.stream()
                    .collect(Collectors.toMap(p -> String.valueOf(p.getId()), p -> p, (v1, v2) -> v2));
            RedisKeyBuild redisKey = RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_CATEGORY_HASH);
            redisCache.putHash(redisKey, programCategoryMap);
        }
    }

    /**
     * 获取节目类型
     *
     * @param programCategoryId 节目类型 ID
     * @return 节目类型实体
     */
    public ProgramCategory getProgramCategory(Long programCategoryId) {
        RedisKeyBuild redisKey = RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_CATEGORY_HASH);
        String programCategoryIdStr = String.valueOf(programCategoryId);

        // 从 Redis 中获取节目类型信息
        ProgramCategory programCategory = redisCache.getForHash(redisKey, programCategoryIdStr, ProgramCategory.class);

        // 如果 Redis 中没有，则从数据库中获取，并且重新置入缓存
        if (Objects.isNull(programCategory)) {
            Map<String, ProgramCategory> programCategoryMap = programCategoryRedisDataInit();
            return programCategoryMap.get(programCategoryIdStr);
        }
        return programCategory;
    }

    /**
     * 初始化节目类型 Redis 数据
     *
     * @return 节目类型 Map
     */
    @ServiceLock(lockType = LockType.Write, name = PROGRAM_CATEGORY_LOCK, keys = {"#all"})
    public Map<String, ProgramCategory> programCategoryRedisDataInit() {
        // 待返回的结果，以节目类型ID为键，节目类型为值的映射
        Map<String, ProgramCategory> programCategoryMap = new HashMap<>(64);

        // 查询所有节目类型列表
        QueryWrapper<ProgramCategory> lambdaQueryWrapper = Wrappers.emptyWrapper();
        List<ProgramCategory> programCategoryList = programCategoryMapper.selectList(lambdaQueryWrapper);

        // 如果查询结果不为空，则构建 Map 并置入缓存
        if (CollectionUtil.isNotEmpty(programCategoryList)) {
            // collect传递的参数分别为：键、值、合并函数（处理键冲突的情况）
            programCategoryMap = programCategoryList.stream()
                    .collect(Collectors.toMap(p -> String.valueOf(p.getId()), p -> p, (v1, v2) -> v2));
            redisCache.putHash(RedisKeyBuild.createRedisKey(RedisKeyManage.PROGRAM_CATEGORY_HASH), programCategoryMap);
        }

        // 返回结果
        return programCategoryMap;
    }
}
