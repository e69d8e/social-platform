package com.li.socialplatform.server.controller;

import com.li.socialplatform.pojo.dto.CommentDTO;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.server.service.ICommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @author e69d8e
 * @since 2025/12/9 18:17
 */
@RestController
@RequestMapping("/comment")
@Tag(name = "评论", description = "帖子评论功能，支持两级评论")
@RequiredArgsConstructor
public class CommentController {
    private final ICommentService commentService;

    @PostMapping
    @Operation(summary = "添加评论", description = "对帖子发表评论或回复评论")
    public Result addComment(@RequestBody CommentDTO commentDTO) {
        return commentService.addComment(commentDTO);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取评论", description = "获取帖子的一级评论列表（游标分页）")
    public Result getComments(
            @Parameter(description = "帖子ID") @PathVariable Long id,
            @Parameter(description = "游标ID") @RequestParam Long lastId,
            @Parameter(description = "偏移量") @RequestParam(defaultValue = "0") Integer offset) {
        return commentService.getComments(id, lastId, offset);
    }
}
