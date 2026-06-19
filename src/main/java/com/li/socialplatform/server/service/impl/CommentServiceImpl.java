package com.li.socialplatform.server.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.common.constant.MessageConstant;
import com.li.socialplatform.common.properties.SystemConstants;
import com.li.socialplatform.common.utils.UserIdUtil;
import com.li.socialplatform.common.utils.UserIntersetScoreUtil;
import com.li.socialplatform.server.mapper.CommentMapper;
import com.li.socialplatform.server.mapper.PostMapper;
import com.li.socialplatform.server.mapper.UserMapper;
import com.li.socialplatform.pojo.dto.CommentDTO;
import com.li.socialplatform.pojo.entity.*;
import com.li.socialplatform.pojo.vo.ChildrenVO;
import com.li.socialplatform.pojo.vo.CommentUserVO;
import com.li.socialplatform.pojo.vo.CommentVO;
import com.li.socialplatform.server.service.ICommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author e69d8e
 * @since 2025/12/9 18:28
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements ICommentService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SystemConstants systemConstants;
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final UserIdUtil userIdUtil;
    private final UserIntersetScoreUtil userIntersetScoreUtil;
    private final PostMapper postMapper;

    @Transactional
    @Override
    public Result addComment(CommentDTO commentDTO) {
        log.info(commentDTO.toString());
        Comment comment = BeanUtil.copyProperties(commentDTO, Comment.class);
        Long userId = userIdUtil.getUserId();
        User user = userMapper.selectById(userId);
        if (!user.getEnabled()) {
            return Result.error(MessageConstant.USER_NOT_ENABLED);
        }
        comment.setUserId(userId);
        long timeMillis = System.currentTimeMillis();
//        LocalDateTime now = LocalDateTime.now();
//        comment.setCreateTime(now);
        // 保存到数据库
        commentMapper.insert(comment);
        log.info(comment.toString());
        // 一级评论保存到缓存
        // 如果是子评论不缓存
        if (comment.getReplyTo() == null) {
            redisTemplate.opsForZSet()
                    .add(KeyConstant.COMMENT_KEY + commentDTO.getPostId(),
                            comment.getId(), timeMillis);
        }
        // 用户兴趣+1
        Post post = postMapper.selectById(comment.getPostId());
        userIntersetScoreUtil.changeScore(userId, post.getCategoryId(), 5);
        return Result.ok(MessageConstant.ADD_COMMENT_SUCCESS, "");
    }

    @Override
    public Result getComments(Long id, Long lastId, Integer offset) {
        // 从缓存中获取一级评论
        Set<ZSetOperations.TypedTuple<Object>> typedTuples = redisTemplate
                .opsForZSet()
                .reverseRangeByScoreWithScores(
                        KeyConstant.COMMENT_KEY + id,
                        0, lastId, offset, Long.parseLong(systemConstants.defaultPageSize));
        if (typedTuples == null || typedTuples.isEmpty()) {
            ScrollResult<CommentVO> objectScrollResult = new ScrollResult<>();
            objectScrollResult.setList(new ArrayList<>());
            objectScrollResult.setMinTime(System.currentTimeMillis());
            objectScrollResult.setOffset(0);
            return Result.ok(objectScrollResult);
        }
        List<Object> list = typedTuples.stream()
                .map(ZSetOperations.TypedTuple::getValue).toList();
        if (list.isEmpty()) {
            ScrollResult<CommentVO> objectScrollResult = new ScrollResult<>();
            objectScrollResult.setList(new ArrayList<>());
            objectScrollResult.setMinTime(System.currentTimeMillis());
            objectScrollResult.setOffset(0);
            return Result.ok(objectScrollResult);
        }
        // 解析id
        List<Long> commentIds = list.stream().map(commentId -> (Long) commentId).toList();

        // 批量查询一级评论
        List<Comment> topLevelComments = commentMapper.selectBatchIds(commentIds);
        Map<Long, Comment> commentMap = topLevelComments.stream()
                .collect(Collectors.toMap(Comment::getId, c -> c));

        // 批量查询所有子评论
        List<Comment> allChildren = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .in(Comment::getParentId, commentIds));
        Map<Long, List<Comment>> childrenMap = allChildren.stream()
                .collect(Collectors.groupingBy(Comment::getParentId));

        // 收集所有需要查询的用户ID
        Set<Long> userIds = new HashSet<>();
        topLevelComments.forEach(c -> userIds.add(c.getUserId()));
        allChildren.forEach(c -> {
            userIds.add(c.getUserId());
            if (c.getReplyTo() != null) {
                userIds.add(c.getReplyTo());
            }
        });

        // 批量查询用户
        Map<Long, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            userMapper.selectBatchIds(userIds).forEach(u -> userMap.put(u.getId(), u));
        }

        // 组装 CommentVO
        List<CommentVO> comments = commentIds.stream()
                .map(commentMap::get)
                .filter(Objects::nonNull)
                .map(comment -> {
                    CommentVO commentVO = BeanUtil.copyProperties(comment, CommentVO.class);
                    // 评论的用户
                    User u = userMap.get(comment.getUserId());
                    if (u != null) {
                        CommentUserVO user = new CommentUserVO();
                        user.setId(u.getId());
                        user.setNickname(u.getNickname());
                        user.setAvatar(u.getAvatar());
                        commentVO.setUser(user);
                    }
                    // 子评论
                    List<Comment> childComments = childrenMap.getOrDefault(comment.getId(), List.of());
                    List<ChildrenVO> children = childComments.stream()
                            .map(child -> {
                                ChildrenVO childrenVO = new ChildrenVO();
                                childrenVO.setId(child.getId());
                                childrenVO.setContent(child.getContent());
                                // 子评论的用户
                                User u1 = userMap.get(child.getUserId());
                                if (u1 != null) {
                                    CommentUserVO user1 = new CommentUserVO();
                                    user1.setId(u1.getId());
                                    user1.setAvatar(u1.getAvatar());
                                    user1.setNickname(u1.getNickname());
                                    childrenVO.setUser(user1);
                                }
                                // 子评论回复的用户
                                if (child.getReplyTo() != null) {
                                    User replyUser = userMap.get(child.getReplyTo());
                                    if (replyUser != null) {
                                        CommentUserVO replyUserVO = new CommentUserVO();
                                        replyUserVO.setId(replyUser.getId());
                                        replyUserVO.setNickname(replyUser.getNickname());
                                        childrenVO.setReplyUser(replyUserVO);
                                    }
                                }
                                return childrenVO;
                            }).toList();
                    commentVO.setChildren(children);
                    return commentVO;
                }).toList();
        // 获取 score
        List<Double> scores = typedTuples.stream()
                .map(ZSetOperations.TypedTuple::getScore).toList();
        // 计算 offset
        int newOffset = 1;
        double score = scores.getFirst();
        for (int i = 1; i < scores.size(); i++) {
            if (score == scores.get(i)) {
                newOffset++;
            } else {
                newOffset = 1;
            }
            score = scores.get(i);
        }
        // 返回 ScrollResult
        ScrollResult<CommentVO> objectScrollResult = new ScrollResult<>();
        objectScrollResult.setList(comments);
        objectScrollResult.setMinTime(scores.getLast().longValue());
        objectScrollResult.setOffset(newOffset);
        return Result.ok(objectScrollResult);
    }

}
