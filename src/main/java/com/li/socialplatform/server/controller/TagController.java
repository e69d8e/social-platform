package com.li.socialplatform.server.controller;

import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.server.service.IPostService;
import com.li.socialplatform.server.service.ITagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tag")
@Tag(name = "标签", description = "标签检索、热门推荐与标签帖子流")
@RequiredArgsConstructor
@Validated
public class TagController {

    private final ITagService tagService;
    private final IPostService postService;

    @GetMapping("/hot")
    @Operation(summary = "获取热门标签", description = "按热度返回全站或指定分类下的热门标签")
    public Result getHotTags(
            @Parameter(description = "可选分类ID") @RequestParam(required = false) Integer categoryId,
            @Parameter(description = "返回数量") @RequestParam(defaultValue = "20") @Min(1) @Max(50) Integer limit) {
        return Result.ok(tagService.getHotTags(categoryId, limit));
    }

    @GetMapping("/search")
    @Operation(summary = "搜索标签", description = "按关键词模糊搜索标签（用于发帖输入联想）")
    public Result searchTags(
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "可选分类ID") @RequestParam(required = false) Integer categoryId,
            @Parameter(description = "返回数量") @RequestParam(defaultValue = "15") @Min(1) @Max(30) Integer limit) {
        return Result.ok(tagService.searchTags(keyword, categoryId, limit));
    }

    @GetMapping("/posts")
    @Operation(summary = "按标签查询帖子", description = "分页查询带指定标签的公开帖子")
    public Result getTagPosts(
            @Parameter(description = "标签ID") @RequestParam(required = false) Long tagId,
            @Parameter(description = "标签名称") @RequestParam(required = false) String tagName,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "8") @Min(1) @Max(100) Integer pageSize) {
        return postService.listPostsByTag(tagId, tagName, pageNum, pageSize);
    }
}
