package com.damai.service;

import com.damai.captcha.model.common.ResponseModel;
import com.damai.captcha.model.vo.CaptchaVO;
import com.baidu.fsg.uid.UidGenerator;
import com.damai.core.RedisKeyManage;
import com.damai.redis.RedisKeyBuild;
import com.damai.service.lua.CheckNeedCaptchaOperate;
import com.damai.vo.CheckNeedCaptchaDataVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户验证码服务类
 * 提供验证码相关功能，包括是否需要验证码验证、获取验证码和验证验证码
 */
@Service
public class UserCaptchaService {

    /**
     * 验证码验证阈值
     * 在一定时间内，超过此阈值的请求将需要验证码验证
     * 这是默认值语法：${verify_captcha_threshold:10}
     */
    @Value("${verify_captcha_threshold:10}")
    private int verifyCaptchaThreshold;

    /**
     * 验证码ID的过期时间
     * 超过这个时间后，验证码ID将无法用于验证
     */
    @Value("${verify_captcha_id_expire_time:60}")
    private int verifyCaptchaIdExpireTime;

    /**
     * 是否总是需要验证码验证的标志
     * 设置为1时，无论请求数量如何，总是需要验证码验证
     */
    @Value("${always_verify_captcha:0}")
    private int alwaysVerifyCaptcha;

    /**
     * 验证码处理接口
     * 用于获取和验证验证码
     */
    @Autowired
    private CaptchaHandle captchaHandle;

    /**
     * UID生成器
     * 用于生成唯一的验证码ID
     */
    @Autowired
    private UidGenerator uidGenerator;

    /**
     * 检查是否需要验证码操作的接口
     * 用于判断当前请求是否需要验证码验证
     */
    @Autowired
    private CheckNeedCaptchaOperate checkNeedCaptchaOperate;

    /**
     * 检查是否需要验证码验证
     *
     * @return 返回一个包含验证码ID和是否需要验证码验证的信息对象
     */
    public CheckNeedCaptchaDataVo checkNeedCaptcha() {
        // 获取当前时间戳
        long currentTimeMillis = System.currentTimeMillis();

        // 生成唯一的验证码ID
        long id = uidGenerator.getUid();

        // 初始化Redis键列表，用于存储验证码相关数据
        List<String> keys = new ArrayList<>();
        keys.add(RedisKeyBuild.createRedisKey(RedisKeyManage.COUNTER_COUNT).getRelKey());
        keys.add(RedisKeyBuild.createRedisKey(RedisKeyManage.COUNTER_TIMESTAMP).getRelKey());
        keys.add(RedisKeyBuild.createRedisKey(RedisKeyManage.VERIFY_CAPTCHA_ID, id).getRelKey());

        // 初始化数据数组，存储验证码验证所需的信息
        String[] data = new String[4];
        data[0] = String.valueOf(verifyCaptchaThreshold);
        data[1] = String.valueOf(currentTimeMillis);
        data[2] = String.valueOf(verifyCaptchaIdExpireTime);
        data[3] = String.valueOf(alwaysVerifyCaptcha);

        // 根据并发量计算是否需要验证码
        Boolean result = checkNeedCaptchaOperate.checkNeedCaptchaOperate(keys, data);

        // 将验证码ID以及是否需要验证码写入返回对象中
        CheckNeedCaptchaDataVo checkNeedCaptchaDataVo = new CheckNeedCaptchaDataVo();
        checkNeedCaptchaDataVo.setCaptchaId(id);
        checkNeedCaptchaDataVo.setVerifyCaptcha(result);

        // 返回结果
        return checkNeedCaptchaDataVo;
    }

    /**
     * 获取验证码
     *
     * @param captchaVO 验证码请求对象，包含获取验证码所需的信息
     * @return 返回包含验证码的响应模型对象
     */
    public ResponseModel getCaptcha(CaptchaVO captchaVO) {
        return captchaHandle.getCaptcha(captchaVO);
    }

    /**
     * 验证验证码
     *
     * @param captchaVO 验证码请求对象，包含待验证的验证码信息
     * @return 返回验证结果的响应模型对象
     */
    public ResponseModel verifyCaptcha(final CaptchaVO captchaVO) {
        return captchaHandle.checkCaptcha(captchaVO);
    }
}

