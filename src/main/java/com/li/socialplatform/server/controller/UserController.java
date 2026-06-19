package com.li.socialplatform.server.controller;

import com.li.socialplatform.common.annotation.RateLimit;
import com.li.socialplatform.pojo.dto.LoginDTO;
import com.li.socialplatform.pojo.dto.RefreshDTO;
import com.li.socialplatform.pojo.dto.UserDTO;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.server.service.ISearchHistoryService;
import com.li.socialplatform.server.service.IUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @author e69d8e
 * @since 2025/12/8 14:30
 */
@RestController
@RequestMapping("/user")
@Tag(name = "用户", description = "用户注册、登录、个人信息、签到、搜索")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final IUserService userService;
    private final ISearchHistoryService searchHistoryService;

    @PostMapping("/login")
    @Operation(summary = "登录", description = "用户名密码登录，返回accessToken和refreshToken")
    public Result login(@Valid @RequestBody LoginDTO loginDTO) {
        return userService.login(loginDTO);
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新Token", description = "使用refreshToken获取新的token对")
    public Result refresh(@Valid @RequestBody RefreshDTO refreshDTO, HttpServletRequest request) {
        return userService.refresh(refreshDTO, request);
    }

    @PostMapping("/logout")
    @Operation(summary = "注销", description = "退出登录，将当前token加入黑名单")
    public Result logout(HttpServletRequest request) {
        return userService.logout(request);
    }

    @PostMapping("/register")
    @Operation(summary = "注册", description = "新用户注册")
    @RateLimit(maxRequests = 5, timeWindow = 300)
    public Result register(@Valid @RequestBody UserDTO userDTO) {
        return userService.register(userDTO);
    }

    @GetMapping("/profile")
    @Operation(summary = "获取当前用户信息", description = "获取当前登录用户的个人信息")
    public Result getUserProfile() {
        return userService.getUserProfile(null);
    }

    @GetMapping("/profile/{id}")
    @Operation(summary = "获取用户信息", description = "根据用户ID获取用户个人信息")
    public Result getUserProfile(
            @Parameter(description = "用户ID") @PathVariable Long id) {
        return userService.getUserProfile(id);
    }

    @PutMapping("/profile")
    @Operation(summary = "修改用户信息", description = "修改当前登录用户的个人信息")
    public Result updateUserProfile(@Valid @RequestBody UserDTO userDTO) {
        return userService.updateUserProfile(userDTO);
    }

    @PutMapping("/password")
    @Operation(summary = "修改密码", description = "修改当前登录用户的密码")
    public Result updatePassword(@Valid @RequestBody UserDTO userDTO) {
        return userService.updatePassword(userDTO);
    }

    @PostMapping("/sign")
    @Operation(summary = "签到", description = "每日签到，返回当月累计签到次数")
    public Result signIn() {
        return userService.signIn();
    }

    @GetMapping("/sign")
    @Operation(summary = "获取签到次数", description = "获取当月累计签到次数")
    public Result signInCount() {
        return userService.signInCount();
    }

    @GetMapping("/list/post")
    @Operation(summary = "搜索帖子", description = "根据关键词和分类搜索帖子（ES全文搜索）")
    public Result listPost(
            @Parameter(description = "搜索关键词") @RequestParam(defaultValue = "") String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "8") @Min(1) @Max(100) Integer pageSize,
            @Parameter(description = "分类ID") @RequestParam(required = false) Integer categoryId) {
        return userService.listPost(keyword, pageNum, pageSize, categoryId);
    }

    @GetMapping("/list/user")
    @Operation(summary = "搜索用户", description = "根据关键词搜索用户（ES搜索）")
    public Result listUser(
            @Parameter(description = "搜索关键词") @RequestParam(defaultValue = "") String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "12") @Min(1) @Max(100) Integer pageSize) {
        return userService.listUser(keyword, pageNum, pageSize);
    }

    @GetMapping("/search-history")
    @Operation(summary = "获取搜索记录", description = "获取当前用户的搜索历史记录")
    public Result getSearchRecords(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer pageSize) {
        return searchHistoryService.getSearchRecords(pageNum, pageSize);
    }

    @DeleteMapping("/search-history/{id}")
    @Operation(summary = "删除搜索记录", description = "删除指定的搜索历史记录")
    public Result deleteSearchRecord(
            @Parameter(description = "记录ID") @PathVariable Long id) {
        return searchHistoryService.deleteSearchRecord(id);
    }

    @DeleteMapping("/search-history")
    @Operation(summary = "清空搜索记录", description = "一键清空当前用户的所有搜索历史")
    public Result clearSearchRecords() {
        return searchHistoryService.clearSearchRecords();
    }
}
