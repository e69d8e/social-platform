package com.li.socialplatform.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
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
@TableName("private_message")
public class PrivateMessage implements Serializable {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    @TableField(value = "conversation_id")
    private Long conversationId;
    @TableField(value = "sender_id")
    private Long senderId;
    @TableField(value = "receiver_id")
    private Long receiverId;
    @TableField(value = "content")
    private String content;
    @TableField(value = "is_read")
    @JsonProperty("isRead")
    private Boolean isRead;
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
