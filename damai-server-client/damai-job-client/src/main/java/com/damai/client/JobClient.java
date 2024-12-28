package com.damai.client;

import com.damai.common.ApiResponse;
import com.damai.dto.JobCallBackDto;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import static com.damai.constant.Constant.SPRING_INJECT_PREFIX_DISTINCTION_NAME;

/**
 * job服务 feign
 **/
@Component
@FeignClient(value = SPRING_INJECT_PREFIX_DISTINCTION_NAME + "-" + "job-service", fallback = JobClientFallback.class)
public interface JobClient {

	/**
	 * 上报任务状态
	 *
	 * @param dto 参数
	 * @return 结果
	 */
	@RequestMapping(value = "jobRunRecord/callBack", method = RequestMethod.POST)
	ApiResponse<Boolean> callBack(@Valid @RequestBody JobCallBackDto dto);
}
