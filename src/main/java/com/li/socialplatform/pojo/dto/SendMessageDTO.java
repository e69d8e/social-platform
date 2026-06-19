package com.li.socialplatform.pojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author e69d8e
 * @since 2025/12/27
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "发送私信请求")
public class SendMessageDTO implements Serializable {
    @NotNull(message = "接收方不能为空")
    @Schema(description = "接收方用户ID", example = "2")
    private Long receiverId;
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 5000, message = "消息内容长度不能超过5000")
    @Schema(description = "消息内容", example = "你好，很高兴认识你！")
    private String content;
}
