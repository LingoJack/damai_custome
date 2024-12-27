package com.damai.service.composite.impl;

import com.damai.dto.ProgramGetDto;
import com.damai.dto.ProgramOrderCreateDto;
import com.damai.enums.BaseCode;
import com.damai.enums.BusinessStatus;
import com.damai.exception.DaMaiFrameException;
import com.damai.service.ProgramService;
import com.damai.service.composite.AbstractProgramCheckHandler;
import com.damai.vo.ProgramVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 节目详情检查处理器
 * 继承自AbstractProgramCheckHandler，用于执行节目详情的验证逻辑
 */
@Component
public class ProgramDetailCheckHandler extends AbstractProgramCheckHandler {

	@Autowired
	private ProgramService programService;

	/**
	 * 执行节目详情的检查逻辑
	 * 主要验证座位选择权限和订单购票数量限制
	 *
	 * @param programOrderCreateDto 节目订单创建DTO，包含订单相关信息
	 */
	@Override
	protected void execute(final ProgramOrderCreateDto programOrderCreateDto) {
		ProgramGetDto programGetDto = new ProgramGetDto();
		programGetDto.setId(programOrderCreateDto.getProgramId());
		ProgramVo programVo = programService.detail(programGetDto);
		if (programVo.getPermitChooseSeat().equals(BusinessStatus.NO.getCode())) {
			if (Objects.nonNull(programOrderCreateDto.getSeatDtoList())) {
				throw new DaMaiFrameException(BaseCode.PROGRAM_NOT_ALLOW_CHOOSE_SEAT);
			}
		}
		Integer seatCount = Optional.ofNullable(programOrderCreateDto.getSeatDtoList()).map(List::size).orElse(0);
		Integer ticketCount = Optional.ofNullable(programOrderCreateDto.getTicketCount()).orElse(0);
		if (seatCount > programVo.getPerOrderLimitPurchaseCount() || ticketCount > programVo.getPerOrderLimitPurchaseCount()) {
			throw new DaMaiFrameException(BaseCode.PER_ORDER_PURCHASE_COUNT_OVER_LIMIT);
		}
	}

	/**
	 * 执行父订单的逻辑
	 *
	 * @return 返回执行的顺序号
	 */
	@Override
	public Integer executeParentOrder() {
		return 1;
	}

	/**
	 * 执行层级的逻辑
	 *
	 * @return 返回执行的顺序号
	 */
	@Override
	public Integer executeTier() {
		return 2;
	}

	/**
	 * 执行订单的逻辑
	 *
	 * @return 返回执行的顺序号
	 */
	@Override
	public Integer executeOrder() {
		return 1;
	}
}
