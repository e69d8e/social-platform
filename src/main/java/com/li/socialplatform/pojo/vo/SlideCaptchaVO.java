package com.li.socialplatform.pojo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 滑块验证码响应体
 *
 * <p>拼图块的画布尺寸与「方形主体在画布内的偏移」由服务端下发：
 * 形状带外凸/内凹圆弧，画布必须覆盖完整包围盒，前端不能按固定边长推导，
 * 否则拼图块会被裁掉一角（历史 bug）。</p>
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

    @Schema(description = "拼图块 PNG base64（透明底，尺寸为 blockWidth×blockHeight）")
    private String blockImage;

    @Schema(description = "缺口纵向位置(px)；横向位置即答案，不下发")
    private Integer y;

    @Schema(description = "拼图块画布宽(px)")
    private Integer blockWidth;

    @Schema(description = "拼图块画布高(px)")
    private Integer blockHeight;

    @Schema(description = "方形主体左边缘在画布内的 x 偏移")
    private Integer blockOffsetX;

    @Schema(description = "方形主体上边缘在画布内的 y 偏移")
    private Integer blockOffsetY;

    @Schema(description = "方形主体边长(px)")
    private Integer size;
}
