package com.li.socialplatform.pojo.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 帖子标签实体
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("tag")
public class Tag implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    @TableField("name")
    private String name;

    @TableField("category_id")
    private Integer categoryId;

    @TableField("use_count")
    private Integer useCount;

    @TableField("is_hot")
    private Boolean isHot;

    @TableField("enabled")
    private Boolean enabled;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
