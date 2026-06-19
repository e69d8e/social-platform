package com.li.socialplatform.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.elasticsearch.annotations.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author e69d8e
 * @since 2025/12/8 14:31
 */
@Document(indexName = "user")
@TableName("user")
@Setting(settingPath = "/es/user-settings.json")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @Id
    private Long id;

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "ik_smart"),
            otherFields = @InnerField(suffix = "edge", type = FieldType.Text, analyzer = "ik_smart_edge_ngram", searchAnalyzer = "ik_smart")
    )
    @TableField(value = "username")
    private String username;

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "ik_smart"),
            otherFields = @InnerField(suffix = "edge", type = FieldType.Text, analyzer = "ik_smart_edge_ngram", searchAnalyzer = "ik_smart")
    )
    @TableField(value = "nickname")
    private String nickname;

    @Transient
    @TableField(value = "password")
    private String password; // 存储加密后的密码

    @Field(type = FieldType.Keyword)
    @TableField(value = "avatar")
    private String avatar;

    @Field(type = FieldType.Text, analyzer = "ik_smart")
    @TableField(value = "bio")
    private String bio;

    @Field(type = FieldType.Integer)
    @TableField(value = "gender")
    private Integer gender;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Field(type = FieldType.Integer)
    @TableField(value = "authority_id")
    private Integer authorityId;

    @Field(type = FieldType.Boolean)
    @TableField(value = "enabled")
    private Boolean enabled;

    @Field(type = FieldType.Boolean)
    @TableField(value = "fans_private")
    private Boolean fansPrivate; // 是否允许他人查看粉丝列表 (0: 允许, 1: 不允许)

    @Field(type = FieldType.Boolean)
    @TableField(value = "follow_private")
    private Boolean followPrivate; // 是否允许他人查看关注列表 (0: 允许, 1: 不允许)

    @Field(type = FieldType.Integer)
    @TableField(value = "fans_count")
    private Integer fansCount; // 粉丝数
}
