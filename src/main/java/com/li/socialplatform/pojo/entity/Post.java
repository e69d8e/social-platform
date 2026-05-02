package com.li.socialplatform.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.time.LocalDateTime;

@Document(indexName = "post")
@TableName("post")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Post implements Serializable {

    @Id
    @TableId(value = "id", type = IdType.AUTO) // 帖子ID通常使用数据库自增
    private Long id;

    @Field(type = FieldType.Keyword)
    @TableField("user_id")
    private Long userId;

    @Field(type = FieldType.Keyword)
    @TableField("cover")
    private String cover;

    @Field(type = FieldType.Text, analyzer = "ik_smart")
    @TableField("title")
    private String title;

    @Field(type = FieldType.Text, analyzer = "ik_smart")
    @TableField("content")
    private String content;

    @Field(type = FieldType.Integer)
    @TableField("category_id")
    private Integer categoryId;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Field(type = FieldType.Boolean)
    @TableField(value = "enabled")
    private Boolean enabled;

    @Field(type = FieldType.Integer)
    @TableField(exist = false)
    private Integer count;
}