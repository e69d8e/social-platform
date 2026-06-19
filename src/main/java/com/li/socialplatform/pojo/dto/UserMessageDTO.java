package com.li.socialplatform.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "用户消息")
@ToString
public class UserMessageDTO {
    @NotNull(message = "用户ID不能为空")
    @Schema(description = "用户id")
    private Long userId;
    @NotBlank(message = "记忆ID不能为空")
    @Schema(description = "记忆id")
    private String memoryId;
    @NotBlank(message = "消息内容不能为空")
    @Schema(description = "内容")
    private String content;
}
