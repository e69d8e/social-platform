package com.li.socialplatform.server.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.socialplatform.common.constant.MessageConstant;
import com.li.socialplatform.common.utils.UserIdUtil;
import com.li.socialplatform.pojo.dto.SendMessageDTO;
import com.li.socialplatform.pojo.entity.*;
import com.li.socialplatform.pojo.vo.ConversationVO;
import com.li.socialplatform.pojo.vo.PrivateMessageVO;
import com.li.socialplatform.server.mapper.PrivateConversationMapper;
import com.li.socialplatform.server.mapper.PrivateMessageMapper;
import com.li.socialplatform.server.mapper.UserMapper;
import com.li.socialplatform.server.service.IPrivateMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author e69d8e
 * @since 2025/12/27
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PrivateMessageServiceImpl extends ServiceImpl<PrivateMessageMapper, PrivateMessage> implements IPrivateMessageService {

    private final PrivateMessageMapper privateMessageMapper;
    private final PrivateConversationMapper privateConversationMapper;
    private final UserMapper userMapper;
    private final UserIdUtil userIdUtil;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    @Override
    public Result sendMessage(SendMessageDTO dto) {
        // 获取当前用户
        Long userId = userIdUtil.getUserId();
        User sender = userMapper.selectById(userId);
        if (!sender.getEnabled()) {
            return Result.error(MessageConstant.USER_NOT_ENABLED);
        }
        // 验证消息内容
        if (dto.getContent() == null || dto.getContent().trim().isEmpty()) {
            return Result.error(MessageConstant.MESSAGE_CONTENT_EMPTY);
        }
        // 验证不能给自己发消息
        if (userId.equals(dto.getReceiverId())) {
            return Result.error(MessageConstant.CANNOT_MESSAGE_SELF);
        }
        // 验证接收方存在
        User receiver = userMapper.selectById(dto.getReceiverId());
        if (receiver == null) {
            return Result.error(MessageConstant.RECEIVER_NOT_EXIST);
        }
        // 归一化用户对
        Long userAId = Math.min(userId, dto.getReceiverId());
        Long userBId = Math.max(userId, dto.getReceiverId());
        // 查找或创建会话
        PrivateConversation conversation = privateConversationMapper.selectOne(
                new LambdaQueryWrapper<PrivateConversation>()
                        .eq(PrivateConversation::getUserAId, userAId)
                        .eq(PrivateConversation::getUserBId, userBId));
        if (conversation == null) {
            conversation = new PrivateConversation();
            conversation.setUserAId(userAId);
            conversation.setUserBId(userBId);
            conversation.setUnreadA(0);
            conversation.setUnreadB(0);
            privateConversationMapper.insert(conversation);
        }
        // 插入消息
        PrivateMessage message = new PrivateMessage();
        message.setConversationId(conversation.getId());
        message.setSenderId(userId);
        message.setReceiverId(dto.getReceiverId());
        message.setContent(dto.getContent().trim());
        message.setIsRead(false);
        privateMessageMapper.insert(message);
        // 更新会话摘要和未读数
        LocalDateTime now = LocalDateTime.now();
        String preview = dto.getContent().trim();
        if (preview.length() > 100) {
            preview = preview.substring(0, 100);
        }
        conversation.setLastMessage(preview);
        conversation.setLastMessageTime(now);
        if (dto.getReceiverId().equals(userAId)) {
            conversation.setUnreadA(conversation.getUnreadA() + 1);
        } else {
            conversation.setUnreadB(conversation.getUnreadB() + 1);
        }
        privateConversationMapper.updateById(conversation);
        // 通过 WebSocket 推送新消息给接收方
        try {
            PrivateMessageVO pushVO = new PrivateMessageVO();
            pushVO.setId(message.getId());
            pushVO.setSenderId(userId);
            pushVO.setSenderNickname(sender.getNickname());
            pushVO.setSenderAvatar(sender.getAvatar());
            pushVO.setContent(message.getContent());
            pushVO.setIsRead(false);
            pushVO.setCreateTime(now);
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(dto.getReceiverId()), "/queue/messages", pushVO);
        } catch (Exception e) {
            log.warn("WebSocket 推送失败（不影响消息发送）: {}", e.getMessage());
        }
        return Result.ok(MessageConstant.MESSAGE_SEND_SUCCESS, "");
    }

    @Override
    public Result getConversationList(Integer pageNum, Integer pageSize) {
        Long userId = userIdUtil.getUserId();
        // 查询当前用户参与的会话，按最后消息时间倒序
        Page<PrivateConversation> page = privateConversationMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<PrivateConversation>()
                        .and(wrapper -> wrapper
                                .eq(PrivateConversation::getUserAId, userId)
                                .or()
                                .eq(PrivateConversation::getUserBId, userId))
                        .orderByDesc(PrivateConversation::getLastMessageTime));
        List<PrivateConversation> records = page.getRecords();
        if (records.isEmpty()) {
            return Result.ok(new ArrayList<>(), 0L);
        }
        // 批量查询对方用户信息
        Set<Long> otherUserIds = records.stream()
                .map(conv -> userId.equals(conv.getUserAId()) ? conv.getUserBId() : conv.getUserAId())
                .collect(Collectors.toSet());
        Map<Long, User> userMap = new HashMap<>();
        userMapper.selectBatchIds(otherUserIds).forEach(u -> userMap.put(u.getId(), u));

        List<ConversationVO> voList = records.stream().map(conv -> {
            ConversationVO vo = new ConversationVO();
            vo.setConversationId(conv.getId());
            // 确定对方用户ID
            Long otherUserId;
            Integer unreadCount;
            if (userId.equals(conv.getUserAId())) {
                otherUserId = conv.getUserBId();
                unreadCount = conv.getUnreadA();
            } else {
                otherUserId = conv.getUserAId();
                unreadCount = conv.getUnreadB();
            }
            vo.setOtherUserId(otherUserId);
            vo.setUnreadCount(unreadCount);
            vo.setLastMessage(conv.getLastMessage());
            vo.setLastMessageTime(conv.getLastMessageTime());
            // 获取对方用户信息
            User otherUser = userMap.get(otherUserId);
            if (otherUser != null) {
                vo.setOtherUserNickname(otherUser.getNickname());
                vo.setOtherUserAvatar(otherUser.getAvatar());
            }
            return vo;
        }).toList();
        return Result.ok(voList, page.getTotal());
    }

    @Override
    public Result getMessageHistory(Long conversationId, Integer pageNum, Integer pageSize) {
        Long userId = userIdUtil.getUserId();
        // 验证会话存在且当前用户是参与者
        PrivateConversation conversation = privateConversationMapper.selectById(conversationId);
        if (conversation == null) {
            return Result.error(MessageConstant.CONVERSATION_NOT_EXIST);
        }
        if (!userId.equals(conversation.getUserAId()) && !userId.equals(conversation.getUserBId())) {
            return Result.error(MessageConstant.NOT_CONVERSATION_PARTICIPANT);
        }
        // 查询消息历史，按时间倒序
        Page<PrivateMessage> page = privateMessageMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<PrivateMessage>()
                        .eq(PrivateMessage::getConversationId, conversationId)
                        .orderByDesc(PrivateMessage::getCreateTime));
        List<PrivateMessage> records = page.getRecords();
        if (records.isEmpty()) {
            return Result.ok(new ArrayList<>(), 0L);
        }
        // 批量查询发送者信息
        Set<Long> senderIds = records.stream().map(PrivateMessage::getSenderId).collect(Collectors.toSet());
        Map<Long, User> senderMap = new HashMap<>();
        userMapper.selectBatchIds(senderIds).forEach(u -> senderMap.put(u.getId(), u));

        List<PrivateMessageVO> voList = records.stream().map(msg -> {
            PrivateMessageVO vo = BeanUtil.copyProperties(msg, PrivateMessageVO.class);
            User sender = senderMap.get(msg.getSenderId());
            if (sender != null) {
                vo.setSenderNickname(sender.getNickname());
                vo.setSenderAvatar(sender.getAvatar());
            }
            return vo;
        }).toList();
        return Result.ok(voList, page.getTotal());
    }

    @Transactional
    @Override
    public Result markAsRead(Long conversationId) {
        Long userId = userIdUtil.getUserId();
        // 验证会话存在且当前用户是参与者
        PrivateConversation conversation = privateConversationMapper.selectById(conversationId);
        if (conversation == null) {
            return Result.error(MessageConstant.CONVERSATION_NOT_EXIST);
        }
        if (!userId.equals(conversation.getUserAId()) && !userId.equals(conversation.getUserBId())) {
            return Result.error(MessageConstant.NOT_CONVERSATION_PARTICIPANT);
        }
        // 批量标记消息为已读
        privateMessageMapper.update(null,
                new LambdaUpdateWrapper<PrivateMessage>()
                        .eq(PrivateMessage::getConversationId, conversationId)
                        .eq(PrivateMessage::getReceiverId, userId)
                        .eq(PrivateMessage::getIsRead, false)
                        .set(PrivateMessage::getIsRead, true));
        // 清零会话未读计数
        if (userId.equals(conversation.getUserAId())) {
            conversation.setUnreadA(0);
        } else {
            conversation.setUnreadB(0);
        }
        privateConversationMapper.updateById(conversation);
        return Result.ok();
    }

    @Override
    public Result getUnreadCount() {
        Long userId = userIdUtil.getUserId();
        // 查询用户是A的会话未读数之和
        List<PrivateConversation> convAsA = privateConversationMapper.selectList(
                new LambdaQueryWrapper<PrivateConversation>()
                        .eq(PrivateConversation::getUserAId, userId)
                        .gt(PrivateConversation::getUnreadA, 0));
        int totalUnread = convAsA.stream().mapToInt(PrivateConversation::getUnreadA).sum();
        // 查询用户是B的会话未读数之和
        List<PrivateConversation> convAsB = privateConversationMapper.selectList(
                new LambdaQueryWrapper<PrivateConversation>()
                        .eq(PrivateConversation::getUserBId, userId)
                        .gt(PrivateConversation::getUnreadB, 0));
        totalUnread += convAsB.stream().mapToInt(PrivateConversation::getUnreadB).sum();
        return Result.ok(totalUnread);
    }
}
