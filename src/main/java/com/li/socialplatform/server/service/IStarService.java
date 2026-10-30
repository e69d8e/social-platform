package com.li.socialplatform.server.service;

import com.li.socialplatform.pojo.entity.Result;

public interface IStarService {

    Result star(Long postId);

    Result listMyStars(Integer pageNum, Integer pageSize);
}
