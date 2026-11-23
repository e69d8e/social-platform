package com.li.socialplatform.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.common.utils.RedisUtil;
import com.li.socialplatform.common.utils.UserIdUtil;
import com.li.socialplatform.pojo.entity.*;
import com.li.socialplatform.server.mapper.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户个人主页统计数据
 *
 * @author e69d8e
 */
@RestController
@RequestMapping("/user/stats")
@Tag(name = "用户统计", description = "个人主页数据可视化接口")
@RequiredArgsConstructor
public class UserStatsController {

    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final LikeMapper likeMapper;
    private final FollowMapper followMapper;
    private final UserMapper userMapper;
    private final RedisUtil redisUtil;
    private final UserIdUtil userIdUtil;

    @GetMapping("/profile/{userId}")
    @Operation(summary = "用户创作统计", description = "发帖数、获赞总数、评论数、粉丝趋势")
    public Result profileStats(@PathVariable Long userId) {
        Map<String, Object> stats = new HashMap<>();

        // 基础计数
        Long postCount = postMapper.selectCount(
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getUserId, userId)
                        .eq(Post::getEnabled, true)
                        .eq(Post::getStatus, "published"));
        stats.put("postCount", postCount);

        Long commentCount = commentMapper.selectCount(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getUserId, userId));
        stats.put("commentCount", commentCount);

        // 获赞总数：所有帖子 like_count 之和
        List<Post> posts = postMapper.selectList(
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getUserId, userId)
                        .eq(Post::getEnabled, true)
                        .eq(Post::getStatus, "published")
                        .select(Post::getId, Post::getLikeCount));
        long totalLikes = posts.stream()
                .mapToLong(p -> p.getLikeCount() != null ? p.getLikeCount() : 0)
                .sum();
        stats.put("totalLikes", totalLikes);

        // 总阅读量
        long totalViews = posts.stream()
                .mapToLong(p -> p.getViewCount() != null ? p.getViewCount() : 0)
                .sum();
        stats.put("totalViews", totalViews);

        // 近30天粉丝趋势（从 ZSet 读分数）
        List<Map<String, Object>> fansTrend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 29; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            long startMillis = date.atStartOfDay()
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            long endMillis = date.plusDays(1).atStartOfDay()
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            Long count = redisUtil.zsets().count(
                    KeyConstant.FANS_LIST_KEY + userId, startMillis, endMillis);
            Map<String, Object> point = new HashMap<>();
            point.put("date", date.toString());
            point.put("count", count != null ? count : 0);
            fansTrend.add(point);
        }
        stats.put("fansTrend", fansTrend);

        return Result.ok(stats);
    }

    @GetMapping("/sign-calendar/{userId}")
    @Operation(summary = "签到热力图数据", description = "返回指定用户近一年的签到记录")
    public Result signCalendar(@PathVariable Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        String username = user.getUsername();
        List<Map<String, Object>> calendar = new ArrayList<>();
        LocalDate today = LocalDate.now();
        // 生成近一年数据
        for (int i = 364; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            String key = KeyConstant.SIGN_IN_KEY + username
                    + date.format(DateTimeFormatter.ofPattern("yyyyMM"));
            Boolean signed = redisUtil.values().getBit(key, date.getDayOfMonth() - 1);
            Map<String, Object> point = new HashMap<>();
            point.put("date", date.toString());
            point.put("signed", Boolean.TRUE.equals(signed));
            calendar.add(point);
        }
        return Result.ok(calendar);
    }

    @PostMapping("/visit/{userId}")
    @Operation(summary = "记录主页访问", description = "记录当前用户访问了指定用户的主页")
    public Result recordVisit(@PathVariable Long userId) {
        Long visitorId = userIdUtil.getUserId();
        if (visitorId == null || visitorId.equals(userId)) {
            return Result.ok();
        }
        String key = KeyConstant.PROFILE_VISIT_KEY + userId;
        redisUtil.zsets().add(key, visitorId, System.currentTimeMillis());
        // 只保留最近 100 条
        redisUtil.zsets().removeRange(key, 0, -101);
        redisUtil.expire(key, 30, java.util.concurrent.TimeUnit.DAYS);
        return Result.ok();
    }

    @GetMapping("/visitors/{userId}")
    @Operation(summary = "最近访客", description = "获取指定用户主页的最近访客")
    public Result recentVisitors(@PathVariable Long userId,
                                  @RequestParam(defaultValue = "10") Integer limit) {
        String key = KeyConstant.PROFILE_VISIT_KEY + userId;
        Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<Object>> tuples =
                redisUtil.zsets().reverseRangeWithScores(key, 0, limit - 1);
        if (tuples == null || tuples.isEmpty()) {
            return Result.ok(List.of(), 0L);
        }
        List<Long> visitorIds = tuples.stream()
                .map(t -> Long.parseLong(t.getValue().toString()))
                .collect(Collectors.toList());
        List<User> users = userMapper.selectBatchIds(visitorIds);
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<Map<String, Object>> visitors = new ArrayList<>();
        for (var tuple : tuples) {
            Long vid = Long.parseLong(tuple.getValue().toString());
            User u = userMap.get(vid);
            if (u != null) {
                Map<String, Object> v = new HashMap<>();
                v.put("id", String.valueOf(u.getId()));
                v.put("nickname", u.getNickname());
                v.put("avatar", u.getAvatar());
                v.put("visitTime", new Date(tuple.getScore().longValue()));
                visitors.add(v);
            }
        }
        return Result.ok(visitors, (long) visitors.size());
    }

    @PutMapping("/top-post/{postId}")
    @Operation(summary = "置顶/取消置顶帖子", description = "在个人主页置顶或取消置顶一篇帖子")
    public Result toggleTopPost(@PathVariable Long postId) {
        Long userId = userIdUtil.getUserId();
        if (userId == null) {
            return Result.error("请先登录");
        }
        Post post = postMapper.selectById(postId);
        if (post == null || !post.getUserId().equals(userId)) {
            return Result.error("帖子不存在");
        }
        boolean newTop = !Boolean.TRUE.equals(post.getIsTop());
        // 如果置顶，先取消其他置顶
        if (newTop) {
            postMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Post>()
                            .eq(Post::getUserId, userId)
                            .eq(Post::getIsTop, true)
                            .set(Post::getIsTop, false));
        }
        postMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Post>()
                        .eq(Post::getId, postId)
                        .set(Post::getIsTop, newTop));
        return Result.ok(newTop ? "已置顶" : "已取消置顶");
    }
}
