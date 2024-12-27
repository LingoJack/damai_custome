package com.damai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料
 * @description: 用户实名认证 dto
 * @author: 阿星不是程序员
 **/
@Data
@Schema(title = "UserAuthenticationDto", description = "用户实名认证")
public class UserAuthenticationDto implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(name = "id", type = "Long", description = "用户id", requiredMode = RequiredMode.REQUIRED)
	@NotNull
	private Long id;

	@Schema(name = "relName", type = "String", description = "用户真实名字", requiredMode = RequiredMode.REQUIRED)
	@NotBlank
	private String relName;

	@Schema(name = "idNumber", type = "String", description = "身份证号码", requiredMode = RequiredMode.REQUIRED)
	@NotBlank
	private String idNumber;

}
