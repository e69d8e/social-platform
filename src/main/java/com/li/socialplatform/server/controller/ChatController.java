package com.li.socialplatform.server.controller;

import com.li.socialplatform.pojo.dto.UserMessageDTO;
import com.li.socialplatform.server.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/chat")
@Tag(name = "聊天")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;

    @PostMapping(produces = "text/stream;charset=utf-8")
    @Operation(summary = "聊天")
    public Flux<String> chat(@RequestBody UserMessageDTO userMessageDTO) {
        return chatService.chat(userMessageDTO);
    }
}
