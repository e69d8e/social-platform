package com.li.socialplatform.server.service;

import com.li.socialplatform.pojo.entity.Result;

public interface IReviewerService {
    Result banPost(Long id);

    Result listBanPost(Integer pageNum, Integer pageSize);

    Result deleteComment(Long id, Long postId);

    Result searchBanPost(String keyword, Integer pageNum, Integer pageSize);
}
