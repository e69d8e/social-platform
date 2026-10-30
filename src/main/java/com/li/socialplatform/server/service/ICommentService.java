package com.li.socialplatform.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.li.socialplatform.pojo.dto.CommentDTO;
import com.li.socialplatform.pojo.entity.Comment;
import com.li.socialplatform.pojo.entity.Result;

public interface ICommentService extends IService<Comment> {
    Result addComment(CommentDTO commentDTO);

    Result getComments(Long id, Long lastId, Integer offset);

    Result likeComment(Long commentId);
}
