package com.li.socialplatform.server.controller;

import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.server.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/session")
@Tag(name = "AI会话", description = "AI聊天会话管理")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @GetMapping
    @Operation(summary = "新建会话", description = "创建一个新的AI聊天会话")
    public Result create() {
        return sessionService.create();
    }

    @DeleteMapping("/{sessionId}")
    @Operation(summary = "删除会话", description = "删除指定的AI聊天会话及其消息记录")
    public Result delete(
            @Parameter(description = "会话ID") @PathVariable("sessionId") String sessionId) {
        return sessionService.delete(sessionId);
    }

    @GetMapping("/all")
    @Operation(summary = "获取所有会话", description = "分页获取当前用户的所有AI聊天会话")
    public Result getAll(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1", required = false, name = "page") Integer page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10", required = false, name = "size") Integer size) {
        return sessionService.getSessions(page, size);
    }

    @GetMapping("/{sessionId}")
    @Operation(summary = "获取会话记录", description = "获取指定会话的消息记录")
    public Result getSession(
            @Parameter(description = "会话ID") @PathVariable("sessionId") String sessionId) {
        return sessionService.getSession(sessionId);
    }
}
