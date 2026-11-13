package com.li.socialplatform.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 黑名单列表项
 *
 * @author e69d8e
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "黑名单列表项")
public class BlacklistItemVO implements Serializable {
    @Schema(description = "被拉黑用户ID")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    private String username;

    private String nickname;

    private String avatar;

    @Schema(description = "被拉黑用户是否仍可用（封禁/注销时用于前端标注）")
    private Boolean enabled;

    @Schema(description = "拉黑时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
