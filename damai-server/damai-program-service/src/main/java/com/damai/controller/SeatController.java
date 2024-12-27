package com.damai.controller;

import com.damai.common.ApiResponse;
import com.damai.dto.SeatAddDto;
import com.damai.dto.SeatBatchAddDto;
import com.damai.dto.SeatListDto;
import com.damai.service.SeatService;
import com.damai.vo.SeatRelateInfoVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SeatController类负责处理与座位相关的HTTP请求，
 * 提供座位添加、批量添加和查询座位相关信息的功能。
 */
@RestController
@RequestMapping("/seat")
@Tag(name = "seat", description = "座位")
public class SeatController {

	/**
	 * 注入SeatService服务，用于处理与座位相关的业务逻辑。
	 */
	@Autowired
	private SeatService seatService;

	/**
	 * 处理单个座位添加的请求。
	 *
	 * @param seatAddDto 用于添加座位的DTO，包含了需要的座位信息。
	 * @return 返回一个包含新添加座位ID的ApiResponse对象。
	 */
	@Operation(summary = "单个座位添加")
	@PostMapping(value = "/add")
	public ApiResponse<Long> add(@Valid @RequestBody SeatAddDto seatAddDto) {
		return ApiResponse.ok(seatService.add(seatAddDto));
	}

	/**
	 * 处理批量座位添加的请求。
	 *
	 * @param seatBatchAddDto 用于批量添加座位的DTO，包含了多个座位的信息。
	 * @return 返回一个表示操作是否成功的ApiResponse对象。
	 */
	@Operation(summary = "批量座位添加")
	@PostMapping(value = "/batch/add")
	public ApiResponse<Boolean> batchAdd(@Valid @RequestBody SeatBatchAddDto seatBatchAddDto) {
		return ApiResponse.ok(seatService.batchAdd(seatBatchAddDto));
	}

	/**
	 * 查询座位相关信息。
	 *
	 * @param seatListDto 包含了用于查询座位信息的条件。
	 * @return 返回一个包含座位相关信息的ApiResponse对象。
	 */
	@Operation(summary = "查询座位相关信息")
	@PostMapping(value = "/relate/info")
	public ApiResponse<SeatRelateInfoVo> relateInfo(@Valid @RequestBody SeatListDto seatListDto) {
		return ApiResponse.ok(seatService.relateInfo(seatListDto));
	}
}
