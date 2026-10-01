package com.li.socialplatform.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(description = "带缺口背景图 PNG base64")
    private String bgImage;

    @Schema(description = "拼图块 PNG base64（L×L 透明底）")
    private String blockImage;

    @Schema(description = "拼图块纵向位置(px)；横向位置即答案，不下发")
    private Integer y;
}
