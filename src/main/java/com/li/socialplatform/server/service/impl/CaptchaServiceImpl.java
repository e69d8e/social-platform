package com.li.socialplatform.server.service.impl;

import com.li.socialplatform.common.constant.CacheTtl;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.common.constant.MessageConstant;
import com.li.socialplatform.common.utils.RedisUtil;
import com.li.socialplatform.common.utils.SlideCaptchaImageUtil;
import com.li.socialplatform.pojo.dto.CaptchaVerifyDTO;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.pojo.vo.SlideCaptchaVO;
import com.li.socialplatform.pojo.vo.VerifyTokenVO;
import com.li.socialplatform.server.service.ICaptchaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 滑块验证码服务实现
 *
 * @author e69d8e
 */
@Service
@RequiredArgsConstructor
public class CaptchaServiceImpl implements ICaptchaService {

    /** 容差：|left - offset| 必须 <= 5（需 >= 前端 accuracy=3） */
    private static final int TOLERANCE = 5;

    /** 拖动耗时合法区间 [200ms, 60s]，用于防程序瞬间拖动 */
    private static final long MIN_DRAG_MILLIS = 200;
    private static final long MAX_DRAG_MILLIS = 60_000;

    private final RedisUtil redisUtil;

    @Override
    public Result generateSlide() {
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        // 缺口位置只在服务端，客户端拿到的是渲染好的图片（答案不再随响应下发）
        SlideCaptchaImageUtil.CaptchaImage image = SlideCaptchaImageUtil.generate();
        redisUtil.set(
                KeyConstant.SLIDE_CAPTCHA_KEY + captchaId,
                String.valueOf(image.x()),
                CacheTtl.CAPTCHA_SECONDS,
                TimeUnit.SECONDS);
        return Result.ok(new SlideCaptchaVO(captchaId, image.bgImage(), image.blockImage(), image.y()));
    }

    @Override
    public Result verifySlide(CaptchaVerifyDTO dto) {
        String key = KeyConstant.SLIDE_CAPTCHA_KEY + dto.getCaptchaId();
        Object raw = redisUtil.get(key);
        if (raw == null) {
            return Result.error(MessageConstant.CAPTCHA_EXPIRED);
        }

        int offset = Integer.parseInt(String.valueOf(raw));
        boolean offsetOk = Math.abs(dto.getLeft() - offset) <= TOLERANCE;
        boolean timingOk = dto.getTimestamp() >= MIN_DRAG_MILLIS && dto.getTimestamp() <= MAX_DRAG_MILLIS;
        if (!offsetOk || !timingOk) {
            // 单次尝试：无论哪种失败都立即作废，防暴力枚举（前端失败后会自动刷新新验证码）
            redisUtil.delete(key);
            return Result.error(!offsetOk ? MessageConstant.CAPTCHA_VERIFY_FAIL : MessageConstant.CAPTCHA_TIMING_ERROR);
        }

        // 一次性：校验通过立即作废 captcha
        redisUtil.delete(key);

        String verifyToken = UUID.randomUUID().toString().replace("-", "");
        redisUtil.set(
                KeyConstant.SLIDE_VERIFY_KEY + verifyToken,
                "1",
                CacheTtl.CAPTCHA_SECONDS,
                TimeUnit.SECONDS);
        return Result.ok(new VerifyTokenVO(verifyToken));
    }

    @Override
    public boolean consumeVerifyToken(String verifyToken) {
        if (verifyToken == null || verifyToken.isEmpty()) {
            return false;
        }
        // DEL 返回 true 表示存在并被删除（原子消费，防止重放）
        Boolean deleted = redisUtil.delete(KeyConstant.SLIDE_VERIFY_KEY + verifyToken);
        return Boolean.TRUE.equals(deleted);
    }
}
