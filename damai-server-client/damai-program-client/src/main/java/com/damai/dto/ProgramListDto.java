package com.damai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 主页节目列表查询 dto
 * 用于主页节目列表查询的请求数据传输对象，定义了查询所需的参数
 **/
@Data
@Schema(title = "ProgramListDto", description = "主页节目列表")
public class ProgramListDto {

	/**
	 * 所在区域id
	 * 定义节目所在区域的标识符，用于筛选特定区域的节目
	 */
	@Schema(name = "areaId", type = "Long", description = "所在区域id")
	private Long areaId;

	/**
	 * 父节目类型id集合
	 * 包含一个或多个父节目类型的标识符，用于筛选属于这些类型的节目
	 * 此字段是必须的，且最多允许4个父节目类型id
	 */
	@Schema(name = "parentProgramCategoryIds", type = "Long[]", description = "父节目类型id集合", requiredMode = RequiredMode.REQUIRED)
	@NotNull
	@Size(max = 4)
	private List<Long> parentProgramCategoryIds;
}
