package com.damai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 用户和购票人查询 dto
 * @author: 阿星不是程序员
 **/
@Data
@Schema(title = "UserGetAndTicketUserListDto", description = "查询用户以及用户下购票人集合入参")
public class UserGetAndTicketUserListDto {

	@Schema(name = "id", type = "Long", description = "用户id", requiredMode = RequiredMode.REQUIRED)
	@NotNull
	private Long userId;
}