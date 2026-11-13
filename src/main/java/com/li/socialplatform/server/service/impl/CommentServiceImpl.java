package com.li.socialplatform.server.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.socialplatform.common.constant.AuthorityConstant;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.common.constant.MessageConstant;
import com.li.socialplatform.common.properties.SystemConstants;
import com.li.socialplatform.common.utils.BlacklistUtil;
import com.li.socialplatform.common.utils.RedisUtil;
import com.li.socialplatform.common.utils.UserIdUtil;
import com.li.socialplatform.common.utils.UserIntersetScoreUtil;
import com.li.socialplatform.server.mapper.CommentMapper;
import com.li.socialplatform.server.mapper.PostMapper;
import com.li.socialplatform.server.mapper.UserMapper;
import com.li.socialplatform.pojo.dto.CommentDTO;
import com.li.socialplatform.pojo.entity.*;
import com.li.socialplatform.server.mapper.CommentLikeRecordMapper;
import com.li.socialplatform.pojo.vo.ChildrenVO;
import com.li.socialplatform.pojo.vo.CommentUserVO;
import com.li.socialplatform.pojo.vo.CommentVO;
import com.li.socialplatform.server.repository.PostElasticsearchRepository;
import com.li.socialplatform.server.service.ICommentService;
import com.li.socialplatform.server.service.ITagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final RedisUtil redisUtil;
    private final SystemConstants systemConstants;
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final UserIdUtil userIdUtil;
    private final UserIntersetScoreUtil userIntersetScoreUtil;
    private final PostMapper postMapper;
    private final CommentLikeRecordMapper commentLikeRecordMapper;
    private final BlacklistUtil blacklistUtil;
    private final ITagService tagService;
    private final PostElasticsearchRepository postElasticsearchRepository;

    @Transactional
    @Override
    public Result addComment(CommentDTO commentDTO) {
        log.info(commentDTO.toString());
        Comment comment = BeanUtil.copyProperties(commentDTO, Comment.class);
        Long userId = userIdUtil.getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error(MessageConstant.USER_NOT_FOUND);
        }
        if (!user.getEnabled()) {
            return Result.error(MessageConstant.USER_NOT_ENABLED);
        }
        comment.setUserId(userId);
        // 黑名单：看不见的帖子不允许评论（与私密/封禁帖同一套「不存在」文案）
        Post target = postMapper.selectById(commentDTO.getPostId());
        if (target == null
                || !Boolean.TRUE.equals(target.getEnabled())
                || blacklistUtil.isHidden(userId, target.getUserId())) {
            return Result.error(MessageConstant.POST_NOT_EXIST);
        }
        long timeMillis = System.currentTimeMillis();
//        LocalDateTime now = LocalDateTime.now();
//        comment.setCreateTime(now);
        // 保存到数据库
        commentMapper.insert(comment);
        log.info(comment.toString());
        // 一级评论保存到缓存
        // 如果是子评论不缓存
        if (comment.getReplyTo() == null) {
            redisUtil.zsets()
                    .add(KeyConstant.COMMENT_KEY + commentDTO.getPostId(),
                            comment.getId(), timeMillis);
        }
        // 递增帖子评论数并更新兴趣画像（分类 +5、标签 +6）
        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, comment.getPostId())
                .setSql("comment_count = comment_count + 1"));
        Post post = postMapper.selectById(comment.getPostId());
        if (post != null) {
            List<Long> tagIds = tagService.getTagIdsByPostIds(List.of(post.getId()))
                    .getOrDefault(post.getId(), List.of());
            userIntersetScoreUtil.recordPostInteraction(userId, post.getCategoryId(), tagIds, 5, 6);
            try {
                postElasticsearchRepository.findById(post.getId()).ifPresent(esPost -> {
                    esPost.setCommentCount(post.getCommentCount());
                    postElasticsearchRepository.save(esPost);
                });
            } catch (Exception e) {
                log.warn("同步评论数到 ES 失败: postId={}, error={}", post.getId(), e.getMessage());
            }
        }
        return Result.ok(MessageConstant.ADD_COMMENT_SUCCESS, "");
    }

    @Override
    public Result getComments(Long id, Long lastId, Integer offset) {
        String commentKey = KeyConstant.COMMENT_KEY + id;
        // 一级评论总数：Result 默认 total=0，前端把默认值当真实总数导致徽标恒为 0，
        // 这里显式带上 ZSet 基数（增删评论都同步维护该 ZSet，即为实时总数）
        Long total = redisUtil.zsets().zCard(commentKey);
        // 从缓存中获取一级评论
        Set<ZSetOperations.TypedTuple<Object>> typedTuples = redisUtil
                .zsets()
                .reverseRangeByScoreWithScores(
                        commentKey,
                        0, lastId, offset, Long.parseLong(systemConstants.defaultPageSize));
        if (typedTuples == null || typedTuples.isEmpty()) {
            ScrollResult<CommentVO> objectScrollResult = new ScrollResult<>();
            objectScrollResult.setList(new ArrayList<>());
            objectScrollResult.setMinTime(System.currentTimeMillis());
            objectScrollResult.setOffset(0);
            return Result.ok(objectScrollResult, total);
        }
        List<Object> list = typedTuples.stream()
                .map(ZSetOperations.TypedTuple::getValue).toList();
        if (list.isEmpty()) {
            ScrollResult<CommentVO> objectScrollResult = new ScrollResult<>();
            objectScrollResult.setList(new ArrayList<>());
            objectScrollResult.setMinTime(System.currentTimeMillis());
            objectScrollResult.setOffset(0);
            // 必须带上 total，否则前端会把默认的 0 当成真实总数，评论数徽标瞬间归零
            return Result.ok(objectScrollResult, total);
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

        // 批量查询当前用户对评论的点赞记录
        Set<Long> likedCommentIds = new HashSet<>();
        Long viewerId = userIdUtil.getUserId();
        if (viewerId != null) {
            List<Long> allCommentIds = new ArrayList<>(commentIds);
            allChildren.forEach(c -> allCommentIds.add(c.getId()));
            if (!allCommentIds.isEmpty()) {
                commentLikeRecordMapper.selectList(new LambdaQueryWrapper<CommentLikeRecord>()
                                .eq(CommentLikeRecord::getUserId, viewerId)
                                .in(CommentLikeRecord::getCommentId, allCommentIds))
                        .forEach(r -> likedCommentIds.add(r.getCommentId()));
            }
        }

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
                    // 点赞信息
                    commentVO.setLikeCount(comment.getLikeCount() == null ? 0 : comment.getLikeCount());
                    commentVO.setLiked(likedCommentIds.contains(comment.getId()));
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
                                childrenVO.setLikeCount(child.getLikeCount() == null ? 0 : child.getLikeCount());
                                childrenVO.setLiked(likedCommentIds.contains(child.getId()));
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
        return Result.ok(objectScrollResult, total);
    }

    @Transactional
    @Override
    public Result deleteComment(Long commentId) {
        Long userId = userIdUtil.getUserId();
        if (userId == null) {
            return Result.error(MessageConstant.USER_NOT_LOGIN);
        }
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            return Result.error("评论不存在");
        }
        User operator = userMapper.selectById(userId);
        boolean isModerator = operator != null
                && (AuthorityConstant.REVIEWER.equals(operator.getAuthorityId())
                || AuthorityConstant.ADMIN.equals(operator.getAuthorityId()));
        // 作者本人可删自己的评论，管理员/审核员可删任意评论
        if (!Objects.equals(comment.getUserId(), userId) && !isModerator) {
            return Result.error("无权删除该评论");
        }
        // 一级评论连带删除其下的回复，避免留下孤儿数据
        List<Comment> targets = new ArrayList<>();
        targets.add(comment);
        if (comment.getParentId() == null) {
            targets.addAll(commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                    .eq(Comment::getParentId, commentId)));
        }
        List<Long> targetIds = targets.stream().map(Comment::getId).toList();
        commentLikeRecordMapper.delete(new LambdaQueryWrapper<CommentLikeRecord>()
                .in(CommentLikeRecord::getCommentId, targetIds));
        commentMapper.deleteBatchIds(targetIds);
        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, comment.getPostId())
                .setSql("comment_count = GREATEST(comment_count - " + targetIds.size() + ", 0)"));
        Post updatedPost = postMapper.selectById(comment.getPostId());
        if (updatedPost != null) {
            try {
                postElasticsearchRepository.findById(updatedPost.getId()).ifPresent(esPost -> {
                    esPost.setCommentCount(updatedPost.getCommentCount());
                    postElasticsearchRepository.save(esPost);
                });
            } catch (Exception e) {
                log.warn("同步删除后评论数到 ES 失败: postId={}, error={}", updatedPost.getId(), e.getMessage());
            }
        }
        // 一级评论计数与分页游标都在 ZSet 里，必须同步移除
        redisUtil.zsets().remove(KeyConstant.COMMENT_KEY + comment.getPostId(), commentId);
        return Result.ok(MessageConstant.DELETE_SUCCESS, "");
    }

    @Transactional
    @Override
    public Result likeComment(Long commentId) {
        Long userId = userIdUtil.getUserId();
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            return Result.error("评论不存在");
        }
        CommentLikeRecord existing = commentLikeRecordMapper.selectOne(new LambdaQueryWrapper<CommentLikeRecord>()
                .eq(CommentLikeRecord::getUserId, userId)
                .eq(CommentLikeRecord::getCommentId, commentId));
        boolean liked;
        if (existing != null) {
            commentLikeRecordMapper.deleteById(existing.getId());
            commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
                    .eq(Comment::getId, commentId)
                    .setSql("like_count = GREATEST(like_count - 1, 0)"));
            liked = false;
        } else {
            commentLikeRecordMapper.insert(new CommentLikeRecord(null, commentId, userId, null));
            commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
                    .eq(Comment::getId, commentId)
                    .setSql("like_count = like_count + 1"));
            liked = true;
        }
        Integer count = commentMapper.selectById(commentId).getLikeCount();
        Map<String, Object> data = new HashMap<>();
        data.put("liked", liked);
        data.put("likeCount", count == null ? 0 : count);
        return Result.ok(liked ? "点赞成功" : "已取消点赞", data);
    }

}
