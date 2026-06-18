package com.li.socialplatform.server.controller;

import com.li.socialplatform.pojo.dto.PostDTO;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.server.service.IPostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @author e69d8e
 * @since 2025/12/9 14:21
 */
@RestController
@RequestMapping("/post")
@Tag(name = "帖子", description = "帖子发布、浏览、删除")
@RequiredArgsConstructor
public class PostController {
    private final IPostService postService;

    @PostMapping
    @Operation(summary = "发布帖子", description = "发布一篇新帖子")
    public Result publishPost(@RequestBody PostDTO postDTO) {
        return postService.publishPost(postDTO);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取帖子详情", description = "根据帖子ID获取帖子详情")
    public Result getPost(
            @Parameter(description = "帖子ID") @PathVariable Long id) {
        return postService.getPost(id);
    }

    @GetMapping("/list")
    @Operation(summary = "首页帖子列表", description = "基于用户兴趣的个性化推荐帖子列表（游标分页）")
    public Result listPosts(
            @Parameter(description = "游标ID（首次传当前时间戳）") @RequestParam Long lastId,
            @Parameter(description = "偏移量") @RequestParam(defaultValue = "0") Integer offset) {
        return postService.listPosts(lastId, offset);
    }

    @GetMapping("/follow/list")
    @Operation(summary = "关注帖子列表", description = "已关注用户的帖子列表（游标分页）")
    public Result listFollowPosts(
            @Parameter(description = "游标ID") @RequestParam Long lastId,
            @Parameter(description = "偏移量") @RequestParam(defaultValue = "0") Integer offset) {
        return postService.listFollowPosts(lastId, offset);
    }

    @GetMapping("/user/{id}")
    @Operation(summary = "用户帖子列表", description = "获取某个用户的帖子列表")
    public Result userListPosts(
            @Parameter(description = "用户ID") @PathVariable Long id,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "8") Integer pageSize) {
        return postService.userListPosts(id, pageNum, pageSize);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除帖子", description = "删除当前用户发布的帖子")
    public Result deletePost(
            @Parameter(description = "帖子ID") @PathVariable Long id) {
        return postService.deletePost(id);
    }

    @GetMapping
    @Operation(summary = "生成帖子ID", description = "发布前获取一个唯一的帖子ID")
    public Result generatePostId() {
        return postService.generatePostId();
    }

    @PostMapping("/view/{id}")
    @Operation(summary = "记录浏览量", description = "记录用户对帖子的浏览，同一用户20秒内对同一帖子只计一次")
    public Result recordView(
            @Parameter(description = "帖子ID") @PathVariable Long id) {
        return postService.recordView(id);
    }

    @PostMapping("/migrate/home")
    @Operation(summary = "迁移帖子到首页帖子表", description = "读取Redis中的帖子列表，将数据迁移到home_post表")
    public Result migratePostListToHomePost() {
        return postService.migratePostListToHomePost();
    }
}
