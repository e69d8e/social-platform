package com.li.socialplatform.server.controller;

import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.server.service.INotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 互动通知控制器
 *
 * @author e69d8e
 */
@RestController
@RequestMapping("/notification")
@Tag(name = "互动通知", description = "点赞/评论/回复/关注等互动通知")
@RequiredArgsConstructor
@Validated
public class NotificationController {

    private final INotificationService notificationService;

    @GetMapping("/unread-count")
    @Operation(summary = "未读通知数")
    public Result unreadCount() {
        return Result.ok(notificationService.getUnreadCount());
    }

    @GetMapping("/list")
    @Operation(summary = "通知列表", description = "按时间倒序分页")
    public Result list(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") @Min(1) @Max(50) Integer pageSize,
            @Parameter(description = "通知类型：all/like/comment/follow/system") @RequestParam(required = false) String type) {
        return notificationService.listNotifications(pageNum, pageSize, type);
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "标记单条已读")
    public Result markRead(@PathVariable Long id) {
        return notificationService.markRead(id);
    }

    @PutMapping("/read-all")
    @Operation(summary = "全部标记已读")
    public Result markAllRead() {
        return notificationService.markAllRead();
    }
}
