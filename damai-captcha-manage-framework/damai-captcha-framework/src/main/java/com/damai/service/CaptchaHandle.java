package com.damai.service;

import com.damai.captcha.model.common.ResponseModel;
import com.damai.captcha.model.vo.CaptchaVO;
import com.damai.captcha.service.CaptchaService;
import com.damai.util.RemoteUtil;
import lombok.AllArgsConstructor;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 验证码处理类，用于处理与验证码相关的服务请求
 * 该类包含了获取验证码、检查验证码和验证验证码的功能
 */
@AllArgsConstructor
public class CaptchaHandle {

    /**
     * 注入的验证码服务接口，用于执行验证码相关的业务逻辑
     */
    private final CaptchaService captchaService;

    /**
     * 获取验证码
     * 该方法首先获取当前请求的HttpServletRequest对象，然后从中提取浏览器信息，
     * 并将其设置到CaptchaVO对象中，最后调用captchaService的get方法获取验证码
     *
     * @param captchaVO 验证码的视图对象，包含了获取验证码所需的信息
     * @return 包含验证码信息的响应模型
     */
    public ResponseModel getCaptcha(CaptchaVO captchaVO) {
        // 获取当前请求的属性
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        // 断言请求属性不为空
        assert requestAttributes != null;
        // 从请求属性中获取HttpServletRequest对象
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        // 设置验证码视图对象的浏览器信息
        captchaVO.setBrowserInfo(RemoteUtil.getRemoteId(request));
        // 调用服务层方法获取验证码
        return captchaService.get(captchaVO);
    }

    /**
     * 检查验证码
     * 类似于getCaptcha方法，该方法也首先获取HttpServletRequest对象并提取浏览器信息，
     * 然后调用captchaService的check方法检查验证码
     *
     * @param captchaVO 验证码的视图对象，包含了检查验证码所需的信息
     * @return 检查结果的响应模型
     */
    public ResponseModel checkCaptcha(CaptchaVO captchaVO) {
        // 获取当前请求的属性
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        // 断言请求属性不为空
        assert requestAttributes != null;
        // 从请求属性中获取HttpServletRequest对象
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        // 设置验证码视图对象的浏览器信息
        captchaVO.setBrowserInfo(RemoteUtil.getRemoteId(request));
        // 调用服务层方法检查验证码
        return captchaService.check(captchaVO);
    }

    /**
     * 验证验证码
     * 该方法直接调用captchaService的verification方法进行验证码验证，不涉及浏览器信息的设置
     *
     * @param captchaVO 验证码的视图对象，包含了验证验证码所需的信息
     * @return 验证结果的响应模型
     */
    public ResponseModel verification(CaptchaVO captchaVO) {
        // 调用服务层方法验证验证码
        return captchaService.verification(captchaVO);
    }
}
