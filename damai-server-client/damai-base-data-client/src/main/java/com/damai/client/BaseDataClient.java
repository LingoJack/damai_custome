package com.damai.client;

import com.damai.common.ApiResponse;
import com.damai.dto.AreaGetDto;
import com.damai.dto.AreaSelectDto;
import com.damai.dto.GetChannelDataByCodeDto;
import com.damai.vo.AreaVo;
import com.damai.vo.GetChannelDataVo;
import com.damai.vo.TokenDataVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

import static com.damai.constant.Constant.SPRING_INJECT_PREFIX_DISTINCTION_NAME;

/**
 * 基础数据服务 feign 客户端接口
 * 用于调用基础数据服务的API，提供地区数据、渠道数据、Token数据等查询功能
 */
@Component
@FeignClient(value = SPRING_INJECT_PREFIX_DISTINCTION_NAME + "-" + "base-data-service", fallback = BaseDataClientFallback.class)
public interface BaseDataClient {

	/**
	 * 根据code查询渠道数据
	 *
	 * @param dto 查询参数，包含需要查询的渠道code
	 * @return 渠道数据的API响应对象
	 */
	@PostMapping("/channel/data/getByCode")
	ApiResponse<GetChannelDataVo> getByCode(GetChannelDataByCodeDto dto);

	/**
	 * 查询Token数据
	 *
	 * @return Token数据的API响应对象
	 */
	@PostMapping(value = "/get")
	ApiResponse<TokenDataVo> get();

	/**
	 * 根据ID集合查询地区列表
	 *
	 * @param dto 查询参数，包含需要查询的地区ID列表
	 * @return 地区列表的API响应对象
	 */
	@PostMapping(value = "/area/selectByIdList")
	ApiResponse<List<AreaVo>> selectByIdList(AreaSelectDto dto);

	/**
	 * 根据ID查询地区信息
	 *
	 * @param dto 查询参数，包含需要查询的地区ID
	 * @return 地区信息的API响应对象
	 */
	@PostMapping(value = "/area/getById")
	ApiResponse<AreaVo> getById(AreaGetDto dto);
}
