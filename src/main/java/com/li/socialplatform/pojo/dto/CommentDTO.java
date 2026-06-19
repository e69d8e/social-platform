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
 * @since 2025/12/9 18:25
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommentDTO implements Serializable {
    @NotNull(message = "帖子ID不能为空")
    private Long postId;
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 2000, message = "评论内容长度不能超过2000")
    private String content;
    private Long parentId;
    private Long replyTo;
}
