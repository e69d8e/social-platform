package com.li.socialplatform.pojo.dto;

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
    private String cover;
    private String title;
    private String content;
    private Integer categoryId;
}
