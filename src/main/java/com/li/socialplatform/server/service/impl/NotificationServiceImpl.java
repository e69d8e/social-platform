package com.li.socialplatform.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.common.utils.BlacklistUtil;
import com.li.socialplatform.common.utils.RedisUtil;
import com.li.socialplatform.common.utils.UserIdUtil;
import com.li.socialplatform.pojo.entity.Notification;
import com.li.socialplatform.pojo.entity.Post;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.pojo.entity.User;
import com.li.socialplatform.pojo.vo.NotificationVO;
import com.li.socialplatform.server.mapper.NotificationMapper;
import com.li.socialplatform.server.mapper.PostMapper;
import com.li.socialplatform.server.mapper.UserMapper;
import com.li.socialplatform.server.service.INotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 互动通知服务实现
 *
 * @author e69d8e
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification>
        implements INotificationService {

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;
    private final PostMapper postMapper;
    private final UserIdUtil userIdUtil;
    private final RedisUtil redisUtil;
    private final BlacklistUtil blacklistUtil;
    private final SimpMessagingTemplate messagingTemplate;

    @Async("mvcTaskExecutor")
    @Override
    public void sendNotification(Long receiverId, Long actorId, String type,
                                 Long postId, Long commentId, String content) {
        if (receiverId == null || type == null) {
            return;
        }
        // 自己触发的行为不通知自己
        if (Objects.equals(receiverId, actorId)) {
            return;
        }
        // 拉黑关系不通知（对称隐藏原则延伸到通知）
        if (actorId != null && blacklistUtil.isHidden(receiverId, actorId)) {
            return;
        }
        try {
            Notification notification = new Notification();
            notification.setUserId(receiverId);
            notification.setActorId(actorId);
            notification.setType(type);
            notification.setPostId(postId);
            notification.setCommentId(commentId);
            notification.setContent(content != null && content.length() > 200
                    ? content.substring(0, 200) : content);
            notification.setIsRead(false);
            notificationMapper.insert(notification);

            // Redis 未读数 +1
            String key = KeyConstant.NOTIFICATION_UNREAD_KEY + receiverId;
            redisUtil.increment(key, 1);
            redisUtil.expire(key, 7, TimeUnit.DAYS);

            // WebSocket 实时推送未读数
            try {
                Long unread = getUnreadCountByUserId(receiverId);
                messagingTemplate.convertAndSendToUser(
                        String.valueOf(receiverId), "/queue/notifications", unread);
            } catch (Exception e) {
                log.debug("WebSocket 推送通知未读数失败（用户可能不在线）: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.error("发送通知失败: receiverId={}, type={}, error={}", receiverId, type, e.getMessage());
        }
    }

    @Override
    public Long getUnreadCount() {
        Long userId = userIdUtil.getUserId();
        if (userId == null) {
            return 0L;
        }
        return getUnreadCountByUserId(userId);
    }

    private Long getUnreadCountByUserId(Long userId) {
        String key = KeyConstant.NOTIFICATION_UNREAD_KEY + userId;
        Object cached = redisUtil.values().get(key);
        if (cached != null) {
            return Long.parseLong(cached.toString());
        }
        // 缓存未命中，从 DB 加载
        Long count = notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, false));
        redisUtil.values().set(key, count, 7, TimeUnit.DAYS);
        return count;
    }

    @Override
    public Result listNotifications(Integer pageNum, Integer pageSize, String type) {
        Long userId = userIdUtil.getUserId();
        if (userId == null) {
            return Result.error("请先登录");
        }
        IPage<Notification> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId);
        if (type != null && !type.isBlank() && !"all".equals(type)) {
            if ("comment".equals(type)) {
                wrapper.in(Notification::getType, "comment", "reply");
            } else if ("like".equals(type) || "follow".equals(type) || "system".equals(type)) {
                wrapper.eq(Notification::getType, type);
            }
        }
        wrapper.orderByDesc(Notification::getCreateTime);
        IPage<Notification> result = notificationMapper.selectPage(page, wrapper);

        List<Notification> records = result.getRecords();
        if (records.isEmpty()) {
            return Result.ok(List.of(), 0L);
        }

        // 批量加载触发者信息
        Set<Long> actorIds = records.stream()
                .map(Notification::getActorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> actorMap = actorIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(actorIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        // 批量加载帖子标题
        Set<Long> postIds = records.stream()
                .map(Notification::getPostId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> postTitleMap = postIds.isEmpty() ? Map.of() :
                postMapper.selectBatchIds(postIds).stream()
                        .collect(Collectors.toMap(Post::getId, Post::getTitle));

        List<NotificationVO> vos = records.stream().map(n -> {
            NotificationVO vo = new NotificationVO();
            vo.setId(n.getId());
            vo.setType(n.getType());
            vo.setActorId(n.getActorId());
            if (n.getActorId() != null && actorMap.containsKey(n.getActorId())) {
                User actor = actorMap.get(n.getActorId());
                vo.setActorNickname(actor.getNickname());
                vo.setActorAvatar(actor.getAvatar());
            }
            vo.setPostId(n.getPostId());
            if (n.getPostId() != null) {
                vo.setPostTitle(postTitleMap.getOrDefault(n.getPostId(), null));
            }
            vo.setContent(n.getContent());
            vo.setIsRead(n.getIsRead());
            vo.setCreateTime(n.getCreateTime());
            return vo;
        }).collect(Collectors.toList());

        return Result.ok(vos, result.getTotal());
    }

    @Override
    public Result markRead(Long notificationId) {
        Long userId = userIdUtil.getUserId();
        if (userId == null) {
            return Result.error("请先登录");
        }
        notificationMapper.update(null,
                new LambdaUpdateWrapper<Notification>()
                        .eq(Notification::getId, notificationId)
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, false)
                        .set(Notification::getIsRead, true));
        // 刷新缓存
        refreshUnreadCache(userId);
        return Result.ok();
    }

    @Override
    public Result markAllRead() {
        Long userId = userIdUtil.getUserId();
        if (userId == null) {
            return Result.error("请先登录");
        }
        notificationMapper.update(null,
                new LambdaUpdateWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, false)
                        .set(Notification::getIsRead, true));
        redisUtil.values().set(KeyConstant.NOTIFICATION_UNREAD_KEY + userId, 0L, 7, TimeUnit.DAYS);
        return Result.ok();
    }

    private void refreshUnreadCache(Long userId) {
        Long count = notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, false));
        redisUtil.values().set(KeyConstant.NOTIFICATION_UNREAD_KEY + userId, count, 7, TimeUnit.DAYS);
    }
}
