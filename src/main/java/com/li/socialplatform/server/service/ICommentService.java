package com.li.socialplatform.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.li.socialplatform.pojo.dto.CommentDTO;
import com.li.socialplatform.pojo.entity.Comment;
import com.li.socialplatform.pojo.entity.Result;

public interface ICommentService extends IService<Comment> {
    Result addComment(CommentDTO commentDTO);

    Result getComments(Long id, Long lastId, Integer offset);

    Result likeComment(Long commentId);

    /**
     * 删除评论：评论作者本人或管理员/审核员可删；
     * 删除一级评论时连同其下的回复一起删除。
     */
    Result deleteComment(Long commentId);
}
