package com.li.socialplatform.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author e69d8e
 * @since 2025/12/9 18:18
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("comment")
public class Comment implements Serializable {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    @TableField(value = "post_id")
    private Long postId;
    @TableField(value = "user_id")
    private Long userId;
    @TableField(value = "content")
    private String content;
    @TableField(value = "like_count")
    private Integer likeCount;
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
//    // 不是数据库字段，只是为了返回给前端
//    @TableField(exist = false)
//    private String time;
    @TableField(value = "parent_id")
    private Long parentId;
    @TableField(value = "reply_to")
    private Long replyTo;
}
