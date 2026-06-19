package com.li.socialplatform.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author e69d8e
 * @since 2025/12/9 15:15
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostDTO implements Serializable {
    @NotNull(message = "帖子ID不能为空")
    private Long id;
    private String cover;
    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题长度不能超过100")
    private String title;
    @NotBlank(message = "内容不能为空")
    @Size(max = 12000, message = "内容长度不能超过12000")
    private String content;
    @NotNull(message = "分类不能为空")
    private Integer categoryId;
}
