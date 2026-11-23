package com.li.socialplatform.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.li.socialplatform.common.utils.UserIdUtil;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.pojo.entity.User;
import com.li.socialplatform.server.mapper.UserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户信用分
 *
 * @author e69d8e
 */
@RestController
@RequestMapping("/user/credit")
@Tag(name = "信用分", description = "用户信用分查询与管理")
@RequiredArgsConstructor
public class CreditScoreController {

    private final UserMapper userMapper;
    private final UserIdUtil userIdUtil;

    @GetMapping
    @Operation(summary = "查询信用分", description = "获取当前用户的信用分和等级")
    public Result getMyCredit() {
        Long userId = userIdUtil.getUserId();
        if (userId == null) return Result.error("请先登录");
        User user = userMapper.selectById(userId);
        if (user == null) return Result.error("用户不存在");
        int score = user.getCreditScore() != null ? user.getCreditScore() : 80;
        Map<String, Object> data = new HashMap<>();
        data.put("score", score);
        data.put("level", getLevel(score));
        data.put("badge", user.getBadge() != null ? user.getBadge() : "none");
        return Result.ok(data);
    }

    @PutMapping("/adjust/{userId}")
    @Operation(summary = "调整信用分", description = "管理员调整用户信用分")
    @PreAuthorize("hasRole('ADMIN')")
    public Result adjustCredit(@PathVariable Long userId,
                                @RequestParam Integer delta,
                                @RequestParam(required = false) String reason) {
        User user = userMapper.selectById(userId);
        if (user == null) return Result.error("用户不存在");
        int current = user.getCreditScore() != null ? user.getCreditScore() : 80;
        int newScore = Math.max(0, Math.min(100, current + delta));
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getCreditScore, newScore));
        return Result.ok(Map.of("oldScore", current, "newScore", newScore));
    }

    @PutMapping("/badge/{userId}")
    @Operation(summary = "设置徽章", description = "管理员设置用户徽章")
    @PreAuthorize("hasRole('ADMIN')")
    public Result setBadge(@PathVariable Long userId, @RequestParam String badge) {
        User user = userMapper.selectById(userId);
        if (user == null) return Result.error("用户不存在");
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getBadge, badge));
        return Result.ok("徽章已更新");
    }

    private String getLevel(int score) {
        if (score >= 90) return "优秀";
        if (score >= 70) return "良好";
        if (score >= 50) return "一般";
        if (score >= 30) return "较差";
        return "极差";
    }
}
