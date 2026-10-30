package com.li.socialplatform.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 签到状态响应体。
 *
 * <p>只返回一个数字无法让前端区分「今天签过」和「没签过」，</p>
 * <p>会导致签到按钮永远显示可点击状态，因此显式返回 signedToday。</p>
 *
 * @author e69d8e
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SignInVO {
    /** 当前连续签到天数 */
    private Integer days;
    /** 今天是否已经签到 */
    private Boolean signedToday;
}
