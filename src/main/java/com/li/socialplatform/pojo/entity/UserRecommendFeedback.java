package com.li.socialplatform.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
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
 * 用户推荐负反馈实体（不感兴趣 / 减少推荐）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("user_recommend_feedback")
public class UserRecommendFeedback implements Serializable {

    public static final String TYPE_POST = "POST";
    public static final String TYPE_CATEGORY = "CATEGORY";
    public static final String TYPE_TAG = "TAG";
    public static final String TYPE_AUTHOR = "AUTHOR";
    public static final String FEEDBACK_DISLIKE = "DISLIKE";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("target_type")
    private String targetType;

    @TableField("target_id")
    private Long targetId;

    @TableField("feedback_type")
    private String feedbackType;

    @TableField("reason")
    private String reason;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
