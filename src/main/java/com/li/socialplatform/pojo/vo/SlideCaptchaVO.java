package com.li.socialplatform.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 滑块验证码响应体
 *
 * @author e69d8e
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SlideCaptchaVO {
    private String captchaId;
    private Integer offset;
}
