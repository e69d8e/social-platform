package com.li.socialplatform.server.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.socialplatform.common.utils.RedisUtil;
import com.li.socialplatform.pojo.entity.*;
import com.li.socialplatform.server.mapper.*;
import com.li.socialplatform.server.service.INotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * 每周一早上9点推送用户上周数据周报
 *
 * @author e69d8e
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyDigestTask {

    private final UserMapper userMapper;
    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final LikeMapper likeMapper;
    private final FollowMapper followMapper;
    private final INotificationService notificationService;

    /** 每周一 09:00 执行 */
    @Scheduled(cron = "0 0 9 * * MON")
    public void sendWeeklyDigest() {
        LocalDateTime weekStart = LocalDateTime.now()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusWeeks(1).withHour(0).withMinute(0).withSecond(0);
        LocalDateTime weekEnd = weekStart.plusWeeks(1);

        // 获取所有活跃用户（上周有发帖/评论/点赞的）
        List<User> activeUsers = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .eq(User::getEnabled, true)
                        .last("LIMIT 500"));

        for (User user : activeUsers) {
            try {
                Long uid = user.getId();
                long posts = postMapper.selectCount(new LambdaQueryWrapper<Post>()
                        .eq(Post::getUserId, uid)
                        .eq(Post::getStatus, "published")
                        .ge(Post::getCreateTime, weekStart)
                        .lt(Post::getCreateTime, weekEnd));
                long comments = commentMapper.selectCount(new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getUserId, uid)
                        .ge(Comment::getCreateTime, weekStart)
                        .lt(Comment::getCreateTime, weekEnd));
                long likes = likeMapper.selectCount(new LambdaQueryWrapper<LikeRecord>()
                        .eq(LikeRecord::getUserId, uid)
                        .ge(LikeRecord::getCreateTime, weekStart)
                        .lt(LikeRecord::getCreateTime, weekEnd));
                long newFans = followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getFolloweeId, uid)
                        .ge(Follow::getCreateTime, weekStart)
                        .lt(Follow::getCreateTime, weekEnd));

                if (posts == 0 && comments == 0 && likes == 0 && newFans == 0) continue;

                StringBuilder sb = new StringBuilder("📊 你的上周数据周报\n\n");
                if (posts > 0) sb.append("📝 发布了 ").append(posts).append(" 篇帖子\n");
                if (comments > 0) sb.append("💬 发表了 ").append(comments).append(" 条评论\n");
                if (likes > 0) sb.append("❤️ 送出了 ").append(likes).append(" 个赞\n");
                if (newFans > 0) sb.append("👥 新增了 ").append(newFans).append(" 位粉丝\n");
                sb.append("\n继续加油！💪");

                notificationService.sendNotification(uid, null, "system", null, null, sb.toString());
            } catch (Exception e) {
                log.error("发送周报给用户 {} 失败: {}", user.getId(), e.getMessage());
            }
        }
        log.info("周报推送完成");
    }
}
