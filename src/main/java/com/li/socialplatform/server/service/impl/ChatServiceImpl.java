package com.li.socialplatform.server.service.impl;

import com.li.socialplatform.assistant.Assistant;
import com.li.socialplatform.assistant.TitleAssistant;
import com.li.socialplatform.common.constant.MessageConstant;
import com.li.socialplatform.common.exception.BizException;
import com.li.socialplatform.common.utils.UserIdUtil;
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
    private final UserIdUtil userIdUtil;

    @Override
    public Flux<String> chat(UserMessageDTO userMessageDTO) {
        // 会话属主一律取当前登录用户，不信任客户端传入的 userId
        Long userId = userIdUtil.getUserId();
        // 根据memoryId查询会话
        Session session = sessionMapper.selectById(userMessageDTO.getMemoryId());
        if (session == null) {
            String title = titleAssistant.generateTitle(userMessageDTO.getContent());
            Session s = Session.builder().
                    id(userMessageDTO.getMemoryId()).
                    userId(userId).
                    time(LocalDateTime.now()).
                    name(title).
                    build();
            sessionMapper.insert(s);
        } else if (!session.getUserId().equals(userId)) {
            throw new BizException(MessageConstant.SESSION_NO_PERMISSION);
        }
        return assistant.chat(userMessageDTO.getMemoryId(), userMessageDTO.getContent());
    }
}
