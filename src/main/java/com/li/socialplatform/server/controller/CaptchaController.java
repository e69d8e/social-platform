package com.li.socialplatform.server.controller;

import com.li.socialplatform.common.annotation.RateLimit;
import com.li.socialplatform.pojo.dto.CaptchaVerifyDTO;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.server.service.ICaptchaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 滑块验证码接口
 *
 * @author e69d8e
 */
@RestController
@RequestMapping("/captcha")
@Tag(name = "滑块验证码", description = "滑块验证码生成与校验")
@RequiredArgsConstructor
public class CaptchaController {

    private final ICaptchaService captchaService;

    @GetMapping("/slide")
    @Operation(summary = "获取滑块验证码", description = "返回 captchaId 与渲染好的背景图/拼图块，不包含答案")
    @RateLimit(maxRequests = 20, timeWindow = 60, limitType = RateLimit.LimitType.IP)
    public Result slide() {
        return captchaService.generateSlide();
    }

    @PostMapping("/slide/verify")
    @Operation(summary = "校验滑块验证码", description = "校验滑块位置，通过后返回一次性 verifyToken；失败即作废，需刷新重试")
    @RateLimit(maxRequests = 20, timeWindow = 60, limitType = RateLimit.LimitType.IP)
    public Result verify(@Valid @RequestBody CaptchaVerifyDTO dto) {
        return captchaService.verifySlide(dto);
    }
}
