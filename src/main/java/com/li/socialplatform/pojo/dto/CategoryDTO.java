package com.li.socialplatform.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 分类管理 DTO（新增 / 编辑）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CategoryDTO implements Serializable {

    private Integer id;

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 32, message = "分类名称不能超过32个字符")
    private String name;

    @Size(max = 200, message = "分类简介不能超过200个字符")
    private String description;

    @Size(max = 32, message = "图标标识过长")
    private String icon;

    @Size(max = 16, message = "颜色格式过长")
    private String color;

    private Integer sortOrder;

    private Boolean enabled;
}
