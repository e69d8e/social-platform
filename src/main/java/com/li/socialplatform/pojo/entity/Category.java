package com.li.socialplatform.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author e69d8e
 * @since 2025/12/9 14:16
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("category")
public class Category implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField(value = "name")
    private String name;

    @TableField(value = "description")
    private String description;

    @TableField(value = "icon")
    private String icon;

    @TableField(value = "color")
    private String color;

    @TableField(value = "sort_order")
    private Integer sortOrder;

    @TableField(value = "enabled")
    private Boolean enabled;
}
