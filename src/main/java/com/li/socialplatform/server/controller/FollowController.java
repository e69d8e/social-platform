package com.li.socialplatform.server.controller;

import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.server.service.IFollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * @author e69d8e
 * @since 2025/12/8 23:00
 */
@Slf4j
@RestController
@RequestMapping("/follow")
@Tag(name = "关注", description = "用户关注/取消关注、粉丝列表、好友列表")
@RequiredArgsConstructor
public class FollowController {

    private final IFollowService followService;

    @PostMapping("/{id}")
    @Operation(summary = "关注用户", description = "关注指定用户")
    public Result follow(
            @Parameter(description = "被关注用户ID") @PathVariable Long id) {
        return followService.follow(id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "取消关注", description = "取消关注指定用户")
    public Result cancelFollow(
            @Parameter(description = "被取消关注用户ID") @PathVariable Long id) {
        return followService.cancelFollow(id);
    }

    @GetMapping("/list/{id}")
    @Operation(summary = "用户粉丝列表", description = "获取指定用户的粉丝列表")
    public Result getFollowerList(
            @Parameter(description = "用户ID") @PathVariable Long id,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") Integer pageSize) {
        return followService.getFollowerList(id, pageNum, pageSize);
    }

    @GetMapping("/list/followee/{id}")
    @Operation(summary = "用户关注列表", description = "获取指定用户的关注列表")
    public Result getFolloweeList(
            @Parameter(description = "用户ID") @PathVariable Long id,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") Integer pageSize) {
        return followService.getFolloweeList(id, pageNum, pageSize);
    }

    @GetMapping("/list")
    @Operation(summary = "当前用户粉丝列表", description = "获取当前登录用户的粉丝列表")
    public Result getFollowerList(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") Integer pageSize) {
        return followService.getFollowerList(null, pageNum, pageSize);
    }

    @GetMapping("/followee")
    @Operation(summary = "当前用户关注列表", description = "获取当前登录用户的关注列表")
    public Result getFolloweeList(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") Integer pageSize) {
        return followService.getFolloweeList(null, pageNum, pageSize);
    }

    @GetMapping("/friend")
    @Operation(summary = "好友列表", description = "获取当前用户的互相关注好友列表")
    public Result getFriendList(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") Integer pageSize) {
        return followService.getFriendList(pageNum, pageSize);
    }
}
