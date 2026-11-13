package com.li.socialplatform.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
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
@Schema(description = "会话列表项")
public class ConversationVO implements Serializable {
    @Schema(description = "会话ID")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long conversationId;
    @Schema(description = "对方用户ID")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long otherUserId;
    @Schema(description = "对方昵称")
    private String otherUserNickname;
    @Schema(description = "对方头像URL")
    private String otherUserAvatar;
    @Schema(description = "最后一条消息内容")
    private String lastMessage;
    @Schema(description = "最后消息时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastMessageTime;
    @Schema(description = "当前用户在该会话的未读消息数")
    private Integer unreadCount;
    @Schema(description = "是否可以继续发送消息（任一方向拉黑即为 false，前端据此禁用输入框）")
    private Boolean canSend;
}
