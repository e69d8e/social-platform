package com.li.socialplatform.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author e69d8e
 * @since 2025/12/13 18:46
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostDetailVO implements Serializable {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;
    private String cover;
    private String title;
    private String content;
    private Integer categoryId;
    private String category;
    private List<TagVO> tags;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime publishTime;
    private Boolean liked;
    private Integer likeCount;
    private Boolean starred;
    private Integer starCount;
    private Integer commentCount;
    private Boolean isPrivate;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long userId;
    private String nickname;
    private String avatar;
    private Boolean followed;
    private Boolean enabled;
    private Integer viewCount;
}
