package com.damai.controller;

import com.damai.captcha.model.common.ResponseModel;
import com.damai.captcha.model.vo.CaptchaVO;
import com.damai.common.ApiResponse;
import com.damai.service.UserCaptchaService;
import com.damai.vo.CheckNeedCaptchaDataVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户验证码控制器
 * 提供与用户验证码相关的接口，包括检查是否需要验证码、获取验证码和验证验证码
 */
@RestController
@RequestMapping("/user/captcha")
@Tag(name = "captcha", description = "验证码")
public class UserCaptchaController {

    /**
     * 自动注入用户验证码服务
     * 用于处理验证码相关的业务逻辑
     */
    @Autowired
    private UserCaptchaService userCaptchaService;

    /**
     * 检查是否需要验证码
     *
     * @return 返回检查结果，包括是否需要验证码及可能的验证码类型等信息
     */
    @Operation(summary = "检查是否需要验证码")
    @PostMapping(value = "/check/need")
    public ApiResponse<CheckNeedCaptchaDataVo> checkNeedCaptcha() {
        return ApiResponse.ok(userCaptchaService.checkNeedCaptcha());
    }

    /**
     * 获取验证码
     *
     * @param captchaVO 包含获取验证码所需信息的实体，如手机号等
     * @return 返回获取验证码的结果，包括验证码图片、唯一标识等信息
     */
    @Operation(summary = "获取验证码")
    @PostMapping(value = "/get")
    public ResponseModel getCaptcha(@RequestBody CaptchaVO captchaVO) {
        return userCaptchaService.getCaptcha(captchaVO);
    }

    /**
     * 验证验证码
     *
     * @param captchaVO 包含验证验证码所需信息的实体，如验证码唯一标识和用户输入的验证码值
     * @return 返回验证码验证结果，包括验证是否成功等信息
     */
    @Operation(summary = "验证验证码")
    @PostMapping(value = "/verify")
    public ResponseModel verifyCaptcha(@RequestBody CaptchaVO captchaVO) {
        return userCaptchaService.verifyCaptcha(captchaVO);
    }
}
