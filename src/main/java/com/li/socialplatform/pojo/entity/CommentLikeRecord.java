package com.li.socialplatform.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评论点赞记录
 *
 * @author e69d8e
 * @since 2026/10/28
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("comment_like_record")
public class CommentLikeRecord implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    @TableField(value = "comment_id")
    private Long commentId;
    @TableField(value = "user_id")
    private Long userId;
    @TableField(value = "create_time")
    private LocalDateTime createTime;
}
