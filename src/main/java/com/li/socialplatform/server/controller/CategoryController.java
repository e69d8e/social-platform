package com.li.socialplatform.server.controller;

import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.server.service.ICategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author e69d8e
 * @since 2025/12/12 22:24
 */
@RestController
@RequestMapping("/category")
@Tag(name = "分类", description = "帖子分类")
@RequiredArgsConstructor
public class CategoryController {

    private final ICategoryService categoryService;

    @GetMapping
    @Operation(summary = "获取所有分类", description = "获取所有启用中的帖子分类列表（含简介、主题色、帖子数与热门标签）")
    public Result getCategory() {
        return categoryService.getCategory();
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取单个分类详情", description = "获取分类元信息、公开帖子数和热门标签")
    public Result getCategoryDetail(@Parameter(description = "分类ID") @PathVariable Integer id) {
        return categoryService.getCategoryDetail(id);
    }

    @GetMapping("/{id}/tags")
    @Operation(summary = "获取分类热门标签", description = "获取指定分类下的热门标签列表，用于发帖标签推荐与分类页筛选")
    public Result getCategoryTags(
            @Parameter(description = "分类ID") @PathVariable Integer id,
            @Parameter(description = "返回数量上限") @RequestParam(defaultValue = "15") Integer limit) {
        return categoryService.getCategoryTags(id, limit);
    }
}
