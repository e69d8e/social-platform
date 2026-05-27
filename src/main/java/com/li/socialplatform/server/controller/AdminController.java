package com.li.socialplatform.server.controller;

import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.server.service.IAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @author e69d8e
 * @since 2025/12/10 13:49
 */
@RestController
@RequestMapping("/admin")
@Tag(name = "管理员", description = "管理员操作：封禁用户、设置角色")
@RequiredArgsConstructor
public class AdminController {

    private final IAdminService adminService;

    @PutMapping("/ban/{id}")
    @Operation(summary = "封禁/解封用户", description = "切换用户的封禁状态")
    public Result banUser(
            @Parameter(description = "用户ID") @PathVariable Long id) {
        return adminService.banUser(id);
    }

    @GetMapping("/ban")
    @Operation(summary = "获取封禁用户列表", description = "分页获取被封禁的用户列表")
    public Result getBanUser(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "8") Integer pageSize) {
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
}
