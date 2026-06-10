package com.li.socialplatform.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.li.socialplatform.pojo.dto.PostDTO;
import com.li.socialplatform.pojo.entity.Post;
import com.li.socialplatform.pojo.entity.Result;

public interface IPostService extends IService<Post> {
    Result publishPost(PostDTO postDTO);

    Result getPost(Long id);

    Result listPosts(Long lastId, Integer offset);

    Result listFollowPosts(Long lastId, Integer offset);

    Result userListPosts(Long id, Integer pageNum, Integer pageSize);

    Result deletePost(Long id);

    Result generatePostId();

    Result recordView(Long postId);
}
