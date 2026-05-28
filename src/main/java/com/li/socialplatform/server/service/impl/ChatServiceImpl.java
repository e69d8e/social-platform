package com.li.socialplatform.server.service.impl;

import com.li.socialplatform.assistant.Assistant;
import com.li.socialplatform.assistant.TitleAssistant;
import com.li.socialplatform.pojo.dto.UserMessageDTO;
import com.li.socialplatform.pojo.entity.Session;
import com.li.socialplatform.server.mapper.SessionMapper;
import com.li.socialplatform.server.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final Assistant assistant;
    private final TitleAssistant titleAssistant;
    private final SessionMapper sessionMapper;

    @Override
    public Flux<String> chat(UserMessageDTO userMessageDTO) {
        // 根据memoryId查询会话
        Session session = sessionMapper.selectById(userMessageDTO.getMemoryId());
        if (session == null) {
            String title = titleAssistant.generateTitle(userMessageDTO.getContent());
            Session s = Session.builder().
                    id(userMessageDTO.getMemoryId()).
                    userId(userMessageDTO.getUserId()).
                    time(LocalDateTime.now()).
                    name(title).
                    build();
            sessionMapper.insert(s);
        }
        return assistant.chat(userMessageDTO.getMemoryId(), userMessageDTO.getContent());
    }
}
