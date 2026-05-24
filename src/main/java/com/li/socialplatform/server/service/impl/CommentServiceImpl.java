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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
        List<CommentVO> comments = commentIds.stream()
                .map(commentId -> {
                    Comment comment = commentMapper.selectById(commentId);
                    CommentVO commentVO = BeanUtil.copyProperties(comment, CommentVO.class);
                    // 评论的用户
                    CommentUserVO user = new CommentUserVO();
                    user.setId(comment.getUserId());
                    User u = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getId, comment.getUserId()));
                    user.setNickname(u.getNickname());
                    user.setAvatar(u.getAvatar());
                    commentVO.setUser(user);
                    // 子评论
                    List<Comment> comments1 = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                            .eq(Comment::getParentId, comment.getId()));
                    List<ChildrenVO> children = comments1.stream()
                            .map(comment1 -> {
                                ChildrenVO childrenVO = new ChildrenVO();
                                childrenVO.setId(comment1.getId());
                                childrenVO.setContent(comment1.getContent());
                                // 子评论的用户
                                CommentUserVO user1 = new CommentUserVO();
                                user1.setId(comment1.getUserId());
                                User u1 = userMapper.selectOne(
                                        new LambdaQueryWrapper<User>().eq(User::getId, comment1.getUserId()));
                                user1.setAvatar(u1.getAvatar());
                                user1.setNickname(u1.getNickname());
                                childrenVO.setUser(user1);
                                // 子评论回复的用户
                                CommentUserVO replyUser1 = new CommentUserVO();
                                replyUser1.setId(comment1.getReplyTo());
                                replyUser1.setNickname(userMapper.selectOne(
                                        new LambdaQueryWrapper<User>().eq(User::getId, comment1.getReplyTo())).getNickname());
                                childrenVO.setReplyUser(replyUser1);
                                return childrenVO;
                            }).toList();
                    commentVO.setChildren(children);
                    return commentVO;
                }).toList();
        // 获取 score
        List<Double> scores = typedTuples.stream()
                .map(ZSetOperations.TypedTuple::getScore).toList();
        // 计算 offset
        int nweOffset = 1;
        double score = scores.getFirst();
        for (int i = 1; i < scores.size(); i++) {
            if (score == scores.get(i)) {
                nweOffset++;
            } else {
                nweOffset = 1;
            }
            score = scores.get(i);
        }
        // 返回 ScrollResult
        ScrollResult<CommentVO> objectScrollResult = new ScrollResult<>();
        objectScrollResult.setList(comments);
        objectScrollResult.setMinTime(scores.getLast().longValue());
        objectScrollResult.setOffset(nweOffset);
        return Result.ok(objectScrollResult);
    }

}
