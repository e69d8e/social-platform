package com.li.socialplatform.server.controller;

import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.server.service.ICategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
    @Operation(summary = "获取所有分类", description = "获取帖子的所有分类列表")
    public Result getCategory() {
        return categoryService.getCategory();
    }
}
