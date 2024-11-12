package com.damai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 节目查询 dto
 * 用于节目查询接口的数据传输对象，定义了查询所需的参数
 **/
@Data
@Schema(title = "ProgramGetDto", description = "节目")
public class ProgramGetDto {

    /**
     * 节目ID
     */
    @Schema(name = "id", type = "Long", description = "id", requiredMode = RequiredMode.REQUIRED)
    @NotNull
    private Long id;
}
