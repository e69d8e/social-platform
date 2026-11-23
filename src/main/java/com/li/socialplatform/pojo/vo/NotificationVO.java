package com.li.socialplatform.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 互动通知视图对象
 *
 * @author e69d8e
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "互动通知列表项")
public class NotificationVO implements Serializable {

    @Schema(description = "通知ID")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    @Schema(description = "通知类型：like/comment/reply/follow/system")
    private String type;

    @Schema(description = "触发者用户ID（系统通知为null）")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long actorId;

    @Schema(description = "触发者昵称")
    private String actorNickname;

    @Schema(description = "触发者头像")
    private String actorAvatar;

    @Schema(description = "关联帖子ID")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long postId;

    @Schema(description = "关联帖子标题")
    private String postTitle;

    @Schema(description = "附加内容（评论摘要/系统消息）")
    private String content;

    @Schema(description = "是否已读")
    private Boolean isRead;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
