package com.li.socialplatform.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author e69d8e
 * @since 2025/12/15 23:22
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChildrenVO implements Serializable {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;
    private String content;
    private CommentUserVO user;
    private CommentUserVO replyUser;
    private Integer likeCount; // 点赞数
    private Boolean liked; // 当前用户是否已点赞
}
