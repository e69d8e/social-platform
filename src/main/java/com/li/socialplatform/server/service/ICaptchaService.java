package com.li.socialplatform.server.service;

import com.li.socialplatform.pojo.dto.CaptchaVerifyDTO;
import com.li.socialplatform.pojo.entity.Result;

/**
 * 滑块验证码服务
 *
 * @author e69d8e
 */
public interface ICaptchaService {

    /**
     * 生成滑块验证码，返回 captchaId 与目标偏移量 offset
     */
    Result generateSlide();

    /**
     * 校验滑块位置，通过后返回一次性 verifyToken
     */
    Result verifySlide(CaptchaVerifyDTO dto);

    /**
     * 原子消费 verifyToken（登录/注册时调用），返回 true 表示有效且已作废
     */
    boolean consumeVerifyToken(String verifyToken);
}
