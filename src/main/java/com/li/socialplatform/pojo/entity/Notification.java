package com.li.socialplatform.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 互动通知实体（点赞/评论/回复/关注/系统）
 *
 * @author e69d8e
 */
@TableName("notification")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Notification implements Serializable {

    /** 通知类型：点赞 */
    public static final String TYPE_LIKE = "like";
    /** 通知类型：评论 */
    public static final String TYPE_COMMENT = "comment";
    /** 通知类型：回复 */
    public static final String TYPE_REPLY = "reply";
    /** 通知类型：关注 */
    public static final String TYPE_FOLLOW = "follow";
    /** 通知类型：系统 */
    public static final String TYPE_SYSTEM = "system";

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 接收者用户ID */
    @TableField("user_id")
    private Long userId;

    /** 触发者用户ID（系统通知为 NULL） */
    @TableField("actor_id")
    private Long actorId;

    /** 通知类型 */
    @TableField("type")
    private String type;

    /** 关联帖子ID */
    @TableField("post_id")
    private Long postId;

    /** 关联评论ID */
    @TableField("comment_id")
    private Long commentId;

    /** 附加内容（评论摘要/系统消息文本等） */
    @TableField("content")
    private String content;

    /** 是否已读 */
    @TableField("is_read")
    private Boolean isRead;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
