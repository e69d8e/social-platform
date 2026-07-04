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
 * @since 2025/12/9 18:55
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommentVO implements Serializable {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;
    private String content;
    private CommentUserVO user; // 评论的用户
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    List<ChildrenVO> children; // 子评论
    private CommentUserVO replyUser; // 子评论的回复的用户
}
//[
//        {
//        "id": 1,
//        "content": "讲得很好",
//        "createTime": "2025-03-01",
//        "user": { "id": 1, "nickname": "Tom" },
//        "children": [
//        {
//        "id": 11,
//        "content": "确实如此",
//        "user": { "id": 2, "nickname": "Jerry" },
//        "replyUser": { "id": 1, "nickname": "Tom" }
//        }
//        ]
//        }
//]

