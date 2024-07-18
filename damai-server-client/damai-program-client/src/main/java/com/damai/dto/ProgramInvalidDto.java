package com.damai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @program: 极度真实还原大麦网高并发实战项目。 添加 阿星不是程序员 微信，添加时备注 大麦 来获取项目的完整资料 
 * @description: 节目失效 dto
 * @author: 阿星不是程序员
 **/
@Data
@Schema(title="ProgramInvalidDto", description ="节目失效")
public class ProgramInvalidDto {
    
    @Schema(name ="id", type ="Long", description ="id",requiredMode= RequiredMode.REQUIRED)
    @NotNull
    private Long id;
}
