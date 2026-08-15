package com.li.socialplatform.server.service.impl;

import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.common.constant.MessageConstant;
import com.li.socialplatform.pojo.dto.CaptchaVerifyDTO;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.pojo.vo.SlideCaptchaVO;
import com.li.socialplatform.pojo.vo.VerifyTokenVO;
import com.li.socialplatform.server.service.ICaptchaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
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

    /** captcha / verifyToken 有效期 5 分钟 */
    private static final long TTL_SECONDS = 300;

    /** 合法偏移量范围 [75, 235]，与前端组件参数一致 */
    private static final int OFFSET_MIN = 75;
    private static final int OFFSET_MAX = 235;

    /** 拖动耗时合法区间 [200ms, 60s]，用于防程序瞬间拖动 */
    private static final long MIN_DRAG_MILLIS = 200;
    private static final long MAX_DRAG_MILLIS = 60_000;

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Result generateSlide() {
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        int offset = ThreadLocalRandom.current().nextInt(OFFSET_MIN, OFFSET_MAX + 1); // [75, 235]
        redisTemplate.opsForValue().set(
                KeyConstant.SLIDE_CAPTCHA_KEY + captchaId,
                String.valueOf(offset),
                TTL_SECONDS,
                TimeUnit.SECONDS);
        return Result.ok(new SlideCaptchaVO(captchaId, offset));
    }

    @Override
    public Result verifySlide(CaptchaVerifyDTO dto) {
        String key = KeyConstant.SLIDE_CAPTCHA_KEY + dto.getCaptchaId();
        Object raw = redisTemplate.opsForValue().get(key);
        if (raw == null) {
            return Result.error(MessageConstant.CAPTCHA_EXPIRED);
        }

        int offset = Integer.parseInt(String.valueOf(raw));
        if (Math.abs(dto.getLeft() - offset) > TOLERANCE) {
            return Result.error(MessageConstant.CAPTCHA_VERIFY_FAIL);
        }
        if (dto.getTimestamp() < MIN_DRAG_MILLIS || dto.getTimestamp() > MAX_DRAG_MILLIS) {
            return Result.error(MessageConstant.CAPTCHA_TIMING_ERROR);
        }

        // 一次性：校验通过立即作废 captcha
        redisTemplate.delete(key);

        String verifyToken = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(
                KeyConstant.SLIDE_VERIFY_KEY + verifyToken,
                "1",
                TTL_SECONDS,
                TimeUnit.SECONDS);
        return Result.ok(new VerifyTokenVO(verifyToken));
    }

    @Override
    public boolean consumeVerifyToken(String verifyToken) {
        if (verifyToken == null || verifyToken.isEmpty()) {
            return false;
        }
        // DEL 返回 true 表示存在并被删除（原子消费，防止重放）
        Boolean deleted = redisTemplate.delete(KeyConstant.SLIDE_VERIFY_KEY + verifyToken);
        return Boolean.TRUE.equals(deleted);
    }
}
