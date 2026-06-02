package com.li.socialplatform.server.controller;

import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.server.service.IReviewerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @author e69d8e
 * @since 2025/12/10 13:47
 */
@RestController
@RequestMapping("/reviewer")
@Tag(name = "审核员", description = "审核员操作：封禁帖子、删除评论")
@RequiredArgsConstructor
public class ReviewerController {

    private final IReviewerService reviewerService;

    @PutMapping("/post/{id}")
    @Operation(summary = "封禁/解封帖子", description = "切换帖子的封禁状态")
    public Result banPost(
            @Parameter(description = "帖子ID") @PathVariable Long id) {
        return reviewerService.banPost(id);
    }

    @GetMapping("/post/ban")
    @Operation(summary = "封禁帖子列表", description = "获取当前审核员封禁的帖子列表")
    public Result listBanPost(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "8") Integer pageSize) {
        return reviewerService.listBanPost(pageNum, pageSize);
    }

    @DeleteMapping("/comment/{postId}/{id}")
    @Operation(summary = "删除评论", description = "审核员删除违规评论")
    public Result deleteComment(
            @Parameter(description = "评论ID") @PathVariable("id") Long id,
            @Parameter(description = "帖子ID") @PathVariable("postId") Long postId) {
        return reviewerService.deleteComment(id, postId);
    }

    @GetMapping("/post/ban/search")
    @Operation(summary = "搜索封禁帖子", description = "根据关键词搜索被封禁的帖子")
    public Result searchBanPost(
            @Parameter(description = "搜索关键词") @RequestParam String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "8") Integer pageSize) {
        return reviewerService.searchBanPost(keyword, pageNum, pageSize);
    }
}
