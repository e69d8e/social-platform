package com.li.socialplatform.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.li.socialplatform.pojo.entity.Notification;
import com.li.socialplatform.pojo.entity.Result;

/**
 * 互动通知服务
 *
 * @author e69d8e
 */
public interface INotificationService extends IService<Notification> {

    /**
     * 发送互动通知（异步，不阻塞主流程）。
     * 自动跳过：actor 与接收者是同一人、接收者拉黑了 actor。
     *
     * @param receiverId 接收者
     * @param actorId    触发者（系统通知传 null）
     * @param type       通知类型（like/comment/reply/follow/system）
     * @param postId     关联帖子（可空）
     * @param commentId  关联评论（可空）
     * @param content    附加内容摘要（可空）
     */
    void sendNotification(Long receiverId, Long actorId, String type,
                          Long postId, Long commentId, String content);

    /** 当前用户未读通知数（走 Redis 缓存） */
    Long getUnreadCount();

    /** 分页查询当前用户通知列表（按时间倒序） */
    Result listNotifications(Integer pageNum, Integer pageSize, String type);

    /** 标记单条已读 */
    Result markRead(Long notificationId);

    /** 全部标记已读 */
    Result markAllRead();
}
