package com.li.socialplatform.server.controller;

import com.li.socialplatform.common.annotation.RateLimit;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.server.service.ILikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author e69d8e
 * @since 2025/12/9 21:40
 */
@RestController
@RequestMapping("/like")
@Tag(name = "点赞", description = "帖子点赞/取消点赞")
@RequiredArgsConstructor
public class LikeController {

    private final ILikeService likeService;

    @PutMapping("/{postId}")
    @Operation(summary = "点赞/取消点赞", description = "对帖子进行点赞或取消点赞（切换操作）")
    @RateLimit()
    public Result like(
            @Parameter(description = "帖子ID") @PathVariable Long postId) {
        return likeService.like(postId);
    }
}
