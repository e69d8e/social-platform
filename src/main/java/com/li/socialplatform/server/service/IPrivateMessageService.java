package com.li.socialplatform.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.li.socialplatform.pojo.dto.SendMessageDTO;
import com.li.socialplatform.pojo.entity.PrivateMessage;
import com.li.socialplatform.pojo.entity.Result;

/**
 * @author e69d8e
 * @since 2025/12/27
 */
public interface IPrivateMessageService extends IService<PrivateMessage> {
    Result sendMessage(SendMessageDTO dto);
    Result getConversationList(Integer pageNum, Integer pageSize);

    /** 单个会话详情（聊天页头部使用，避免依赖用户资料接口） */
    Result getConversation(Long conversationId);
    Result getMessageHistory(Long conversationId, Integer pageNum, Integer pageSize);
    Result markAsRead(Long conversationId);
    Result getUnreadCount();
}
