package com.damai.client;

import com.damai.common.ApiResponse;
import com.damai.dto.TicketUserListDto;
import com.damai.dto.UserGetAndTicketUserListDto;
import com.damai.dto.UserIdDto;
import com.damai.vo.TicketUserVo;
import com.damai.vo.UserGetAndTicketUserListVo;
import com.damai.vo.UserVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

import static com.damai.constant.Constant.SPRING_INJECT_PREFIX_DISTINCTION_NAME;

/**
 * 用户服务 feign
 **/
@Component
@FeignClient(value = SPRING_INJECT_PREFIX_DISTINCTION_NAME + "-" + "user-service", fallback = UserClientFallback.class)
public interface UserClient {

	/**
	 * 查询用户(通过id)
	 *
	 * @param dto 参数
	 * @return 结果
	 */
	@PostMapping(value = "/user/getById")
	ApiResponse<UserVo> getById(UserIdDto dto);


	/**
	 * 查询购票人(通过userId)
	 *
	 * @param dto 参数
	 * @return 结果
	 */
	@PostMapping(value = "/ticket/user/list")
	ApiResponse<List<TicketUserVo>> list(TicketUserListDto dto);

	/**
	 * 查询用户和购票人集合
	 *
	 * @param dto 参数
	 * @return 结果
	 */
	@PostMapping(value = "/user/get/user/ticket/list")
	ApiResponse<UserGetAndTicketUserListVo> getUserAndTicketUserList(UserGetAndTicketUserListDto dto);

}
