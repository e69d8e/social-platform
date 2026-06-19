package com.li.socialplatform.server.controller;

import com.li.socialplatform.common.annotation.RateLimit;
import com.li.socialplatform.pojo.dto.SendMessageDTO;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.server.service.IPrivateMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @author e69d8e
 * @since 2025/12/27
 */
@RestController
@RequestMapping("/message")
@Tag(name = "私信", description = "用户私信功能")
@RequiredArgsConstructor
@Validated
public class PrivateMessageController {

    private final IPrivateMessageService privateMessageService;

    @PostMapping
    @Operation(summary = "发送私信", description = "向指定用户发送一条私信，自动创建或复用会话")
    @RateLimit(maxRequests = 20, timeWindow = 60)
    public Result sendMessage(@Valid @RequestBody SendMessageDTO dto) {
        return privateMessageService.sendMessage(dto);
    }

    @GetMapping("/conversations")
    @Operation(summary = "获取会话列表", description = "获取当前用户的所有会话，按最后消息时间倒序，包含对方用户信息和未读数")
    public Result getConversationList(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer pageSize) {
        return privateMessageService.getConversationList(pageNum, pageSize);
    }

    @GetMapping("/history/{conversationId}")
    @Operation(summary = "获取消息历史", description = "获取指定会话的消息记录，按时间倒序分页")
    public Result getMessageHistory(
            @Parameter(description = "会话ID") @PathVariable Long conversationId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer pageSize) {
        return privateMessageService.getMessageHistory(conversationId, pageNum, pageSize);
    }

    @PutMapping("/read/{conversationId}")
    @Operation(summary = "标记已读", description = "将指定会话中所有未读消息标记为已读，并清零未读计数")
    public Result markAsRead(
            @Parameter(description = "会话ID") @PathVariable Long conversationId) {
        return privateMessageService.markAsRead(conversationId);
    }

    @GetMapping("/unread")
    @Operation(summary = "获取未读消息总数", description = "获取当前用户所有会话的未读消息总数")
    public Result getUnreadCount() {
        return privateMessageService.getUnreadCount();
    }
}
