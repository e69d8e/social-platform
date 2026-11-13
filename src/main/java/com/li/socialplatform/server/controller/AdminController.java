package com.li.socialplatform.server.controller;

import com.li.socialplatform.pojo.dto.CategoryDTO;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.server.service.IAdminService;
import com.li.socialplatform.server.service.ICategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @author e69d8e
 * @since 2025/12/10 13:49
 */
@RestController
@RequestMapping("/admin")
@Tag(name = "管理员", description = "管理员操作：封禁用户、设置角色")
@RequiredArgsConstructor
@Validated
public class AdminController {

    private final IAdminService adminService;
    private final ICategoryService categoryService;

    @PutMapping("/ban/{id}")
    @Operation(summary = "封禁/解封用户", description = "切换用户的封禁状态")
    public Result banUser(
            @Parameter(description = "用户ID") @PathVariable Long id) {
        return adminService.banUser(id);
    }

    @GetMapping("/ban")
    @Operation(summary = "获取封禁用户列表", description = "分页获取被封禁的用户列表")
    public Result getBanUser(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "8") @Min(1) @Max(100) Integer pageSize) {
        return adminService.getBanUser(pageNum, pageSize);
    }

    @PutMapping("/review/{id}")
    @Operation(summary = "设为审核员", description = "将指定用户角色设置为审核员")
    public Result setReviewer(
            @Parameter(description = "用户ID") @PathVariable Long id) {
        return adminService.setReviewer(id);
    }

    @PutMapping("/user/{id}")
    @Operation(summary = "设为普通用户", description = "将指定用户角色设置为普通用户")
    public Result setUser(
            @Parameter(description = "用户ID") @PathVariable Long id) {
        return adminService.setUser(id);
    }

    @GetMapping("/ban/search")
    @Operation(summary = "搜索封禁用户", description = "根据关键词搜索被封禁的用户")
    public Result searchBanUser(
            @Parameter(description = "搜索关键词") @RequestParam String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "8") @Min(1) @Max(100) Integer pageSize) {
        return adminService.searchBanUser(keyword, pageNum, pageSize);
    }

    @GetMapping("/dashboard")
    @Operation(summary = "数据面板", description = "获取管理后台统计数据：概览卡片、每日发帖数、每周新增用户数、每日活跃用户数（缺失日期已补 0）")
    public Result getDashboardStats(
            @Parameter(description = "统计天数，默认30天") @RequestParam(required = false) Integer days) {
        return adminService.getDashboardStats(days);
    }

    @GetMapping("/category")
    @Operation(summary = "分类管理列表", description = "获取全部分类（含已停用分类）及其帖子统计")
    public Result listAllCategories() {
        return categoryService.listCategories(true);
    }

    @PostMapping("/category")
    @Operation(summary = "新建分类", description = "创建新的社区板块分类")
    public Result createCategory(@Valid @RequestBody CategoryDTO dto) {
        return categoryService.createCategory(dto);
    }

    @PutMapping("/category")
    @Operation(summary = "更新分类", description = "编辑分类名称、简介、图标、主题色与排序")
    public Result updateCategory(@Valid @RequestBody CategoryDTO dto) {
        return categoryService.updateCategory(dto);
    }

    @PutMapping("/category/{id}/toggle")
    @Operation(summary = "切换分类启用状态", description = "启用或停用指定分类（默认分类不可停用）")
    public Result toggleCategory(@Parameter(description = "分类ID") @PathVariable Integer id) {
        return categoryService.toggleCategoryEnabled(id);
    }
}
