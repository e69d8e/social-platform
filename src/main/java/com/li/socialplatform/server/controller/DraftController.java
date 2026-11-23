package com.li.socialplatform.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.li.socialplatform.common.constant.MessageConstant;
import com.li.socialplatform.common.utils.UserIdUtil;
import com.li.socialplatform.pojo.dto.PostDTO;
import com.li.socialplatform.pojo.entity.Post;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.server.mapper.PostMapper;
import com.li.socialplatform.server.service.IPostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 草稿箱
 *
 * @author e69d8e
 */
@RestController
@RequestMapping("/post/draft")
@Tag(name = "草稿箱", description = "草稿管理和定时发布")
@RequiredArgsConstructor
public class DraftController {

    private final PostMapper postMapper;
    private final UserIdUtil userIdUtil;
    private final IPostService postService;

    @GetMapping("/list")
    @Operation(summary = "草稿列表", description = "获取当前用户的草稿和定时帖子")
    public Result listDrafts(@RequestParam(defaultValue = "1") Integer pageNum,
                              @RequestParam(defaultValue = "20") Integer pageSize) {
        Long userId = userIdUtil.getUserId();
        if (userId == null) return Result.error("请先登录");
        List<Post> drafts = postMapper.selectList(
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getUserId, userId)
                        .in(Post::getStatus, "draft", "scheduled")
                        .eq(Post::getEnabled, true)
                        .orderByDesc(Post::getCreateTime)
                        .last("LIMIT " + (pageNum - 1) * pageSize + "," + pageSize));
        Long total = postMapper.selectCount(
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getUserId, userId)
                        .in(Post::getStatus, "draft", "scheduled")
                        .eq(Post::getEnabled, true));
        return Result.ok(drafts, total);
    }

    @PutMapping("/{id}/publish")
    @Operation(summary = "立即发布草稿", description = "将草稿或定时帖子立即发布")
    public Result publishDraft(@PathVariable Long id) {
        return postService.publishDraft(id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除草稿", description = "删除一篇草稿")
    public Result deleteDraft(@PathVariable Long id) {
        Long userId = userIdUtil.getUserId();
        Post post = postMapper.selectById(id);
        if (post == null || !post.getUserId().equals(userId)) {
            return Result.error(MessageConstant.POST_NOT_EXIST);
        }
        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, id)
                .set(Post::getEnabled, false));
        return Result.ok("已删除");
    }
}
