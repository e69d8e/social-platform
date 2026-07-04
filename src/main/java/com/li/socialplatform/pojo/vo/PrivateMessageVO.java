package com.li.socialplatform.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author e69d8e
 * @since 2025/12/27
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "私信消息详情")
public class PrivateMessageVO implements Serializable {
    @Schema(description = "消息ID")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;
    @Schema(description = "发送者ID")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long senderId;
    @Schema(description = "发送者昵称")
    private String senderNickname;
    @Schema(description = "发送者头像URL")
    private String senderAvatar;
    @Schema(description = "消息内容")
    private String content;
    @Schema(description = "是否已读")
    @JsonProperty("isRead")
    private Boolean isRead;
    @Schema(description = "发送时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
