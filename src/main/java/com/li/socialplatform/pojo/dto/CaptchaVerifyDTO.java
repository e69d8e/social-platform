package com.li.socialplatform.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 滑块验证码校验请求体
 *
 * @author e69d8e
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CaptchaVerifyDTO {
    @NotBlank(message = "captchaId不能为空")
    private String captchaId;

    @NotNull(message = "left不能为空")
    private Double left;

    @NotNull(message = "timestamp不能为空")
    private Long timestamp;
}
