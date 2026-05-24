package com.li.socialplatform.server.controller;

import com.li.socialplatform.pojo.dto.LoginDTO;
import com.li.socialplatform.pojo.dto.RefreshDTO;
import com.li.socialplatform.pojo.dto.UserDTO;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.server.service.ISearchHistoryService;
import com.li.socialplatform.server.service.IUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @author e69d8e
 * @since 2025/12/8 14:30
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final IUserService userService;
    private final ISearchHistoryService searchHistoryService;

    @PostMapping("/login")
    public Result login(@RequestBody LoginDTO loginDTO) {
        return userService.login(loginDTO);
    }

    @PostMapping("/refresh")
    public Result refresh(@RequestBody RefreshDTO refreshDTO, HttpServletRequest request) {
        return userService.refresh(refreshDTO, request);
    }

    @PostMapping("/logout")
    public Result logout(HttpServletRequest request) {
        return userService.logout(request);
    }

    // 注册
    @PostMapping("/register")
    public Result register(@RequestBody UserDTO userDTO) {
        return userService.register(userDTO);
    }

    // 获取当前用户信息
    @GetMapping("/profile")
    public Result getUserProfile() {
        return userService.getUserProfile(null);
    }

    // 获取用户信息
    @GetMapping("/profile/{id}")
    public Result getUserProfile(@PathVariable Long id) {
        return userService.getUserProfile(id);
    }

    // 修改用户信息
    @PutMapping("/profile")
    public Result updateUserProfile(@RequestBody UserDTO userDTO) {
        return userService.updateUserProfile(userDTO);
    }

    // 修改密码
    @PutMapping("/password")
    public Result updatePassword(@RequestBody UserDTO userDTO) {
        return userService.updatePassword(userDTO);
    }

    // 签到 返回当月累计签到次数
    @PostMapping("/sign")
    public Result signIn() {
        return userService.signIn();
    }

    // 获取当月签到次数
    @GetMapping("/sign")
    public Result signInCount() {
        return userService.signInCount();
    }

    // 用户搜索帖子
    @GetMapping("/list/post")
    public Result listPost(@RequestParam(defaultValue = "") String keyword,
                           @RequestParam(defaultValue = "1") Integer pageNum,
                           @RequestParam(defaultValue = "8") Integer pageSize,
                           @RequestParam(required = false) Integer categoryId
    ) {
        return userService.listPost(keyword, pageNum, pageSize, categoryId);
    }

    // 用户搜索用户
    @GetMapping("/list/user")
    public Result listUser(@RequestParam(defaultValue = "") String keyword,
                           @RequestParam(defaultValue = "1") Integer pageNum,
                           @RequestParam(defaultValue = "12") Integer pageSize
    ) {
    return userService.listUser(keyword, pageNum, pageSize);
    }

    // 获取搜索记录
    @GetMapping("/search-history")
    public Result getSearchRecords(@RequestParam(defaultValue = "1") Integer pageNum,
                                   @RequestParam(defaultValue = "20") Integer pageSize) {
        return searchHistoryService.getSearchRecords(pageNum, pageSize);
    }

    // 删除指定搜索记录
    @DeleteMapping("/search-history/{id}")
    public Result deleteSearchRecord(@PathVariable Long id) {
        return searchHistoryService.deleteSearchRecord(id);
    }

    // 一键清空搜索记录
    @DeleteMapping("/search-history")
    public Result clearSearchRecords() {
        return searchHistoryService.clearSearchRecords();
    }
}
