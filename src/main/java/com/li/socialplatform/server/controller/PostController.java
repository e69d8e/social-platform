package com.li.socialplatform.server.controller;

import com.li.socialplatform.common.annotation.RateLimit;
import com.li.socialplatform.pojo.dto.PostDTO;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.server.service.IPostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @author e69d8e
 * @since 2025/12/9 14:21
 */
@RestController
@RequestMapping("/post")
@Tag(name = "帖子", description = "帖子发布、浏览、删除")
@RequiredArgsConstructor
@Validated
public class PostController {
    private final IPostService postService;

    @PostMapping
    @Operation(summary = "发布帖子", description = "发布一篇新帖子")
    @RateLimit(maxRequests = 5, timeWindow = 60)
    public Result publishPost(@Valid @RequestBody PostDTO postDTO) {
        return postService.publishPost(postDTO);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取帖子详情", description = "根据帖子ID获取帖子详情")
    public Result getPost(
            @Parameter(description = "帖子ID") @PathVariable Long id) {
        return postService.getPost(id);
    }

    @GetMapping("/list")
    @Operation(summary = "首页帖子列表", description = "支持个性化推荐(recommend)、热门(hot)与最新(latest)三种模式及服务端换一换")
    public Result listPosts(
            @Parameter(description = "游标ID（latest 模式首屏可不传或传当前时间戳）") @RequestParam(required = false) Long lastId,
            @Parameter(description = "偏移量") @RequestParam(defaultValue = "0") Integer offset,
            @Parameter(description = "信息流模式: recommend / hot / latest") @RequestParam(defaultValue = "recommend") String mode,
            @Parameter(description = "是否触发换一换刷新") @RequestParam(defaultValue = "false") Boolean refresh,
            @Parameter(description = "会话随机种子") @RequestParam(required = false) Long seed,
            @RequestHeader(value = "X-Visitor-Id", required = false) String visitorId) {
        return postService.listPosts(lastId, offset, mode, refresh, seed, visitorId);
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
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "8") @Min(1) @Max(100) Integer pageSize) {
        return postService.userListPosts(id, pageNum, pageSize);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除帖子", description = "删除当前用户发布的帖子")
    public Result deletePost(
            @Parameter(description = "帖子ID") @PathVariable Long id) {
        return postService.deletePost(id);
    }

    @PutMapping("/privacy/{id}")
    @Operation(summary = "设置帖子私密状态", description = "切换帖子的私密状态（仅作者本人可操作）")
    public Result updatePostPrivacy(
            @Parameter(description = "帖子ID") @PathVariable Long id,
            @Parameter(description = "是否私密") @RequestParam Boolean isPrivate) {
        return postService.updatePostPrivacy(id, isPrivate);
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
    @Operation(summary = "迁移帖子到首页帖子表", description = "读取Redis中的帖子列表，将数据迁移到home_post表（仅管理员）")
    // 数据迁移属于运维操作，任何登录用户都能触发会带来数据被反复写库的风险
    @PreAuthorize("hasRole('ADMIN')")
    public Result migratePostListToHomePost() {
        return postService.migratePostListToHomePost();
    }
}
