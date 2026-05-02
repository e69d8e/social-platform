package com.li.socialplatform.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author e69d8e
 * @since 2025/12/9 15:43
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PostVO implements Serializable {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long userId;

    private String title;

    private String content;

    private String categoryName;

    private String cover;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    private Boolean liked; // 是否点赞
    private Integer count; // 点赞数

    private Boolean enabled;
}
