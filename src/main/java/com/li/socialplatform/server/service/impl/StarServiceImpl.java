package com.li.socialplatform.server.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.socialplatform.common.constant.MessageConstant;
import com.li.socialplatform.common.utils.BlacklistUtil;
import com.li.socialplatform.common.utils.DataCacheUtil;
import com.li.socialplatform.common.utils.UserIdUtil;
import com.li.socialplatform.common.utils.UserIntersetScoreUtil;
import com.li.socialplatform.pojo.entity.Post;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.pojo.entity.StarRecord;
import com.li.socialplatform.pojo.entity.User;
import com.li.socialplatform.pojo.vo.PostVO;
import com.li.socialplatform.server.mapper.PostMapper;
import com.li.socialplatform.server.mapper.StarRecordMapper;
import com.li.socialplatform.server.mapper.UserMapper;
import com.li.socialplatform.server.service.IStarService;
import com.li.socialplatform.server.service.ITagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author e69d8e
 * @since 2026/10/28
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StarServiceImpl extends ServiceImpl<StarRecordMapper, StarRecord> implements IStarService {

    private final StarRecordMapper starRecordMapper;
    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final UserIdUtil userIdUtil;
    private final DataCacheUtil dataCacheUtil;
    private final BlacklistUtil blacklistUtil;
    private final UserIntersetScoreUtil userIntersetScoreUtil;
    private final ITagService tagService;

    @Transactional
    @Override
    public Result star(Long postId) {
        Long userId = userIdUtil.getUserId();
        Post post = postMapper.selectById(postId);
        if (post == null || !Boolean.TRUE.equals(post.getEnabled())) {
            return Result.error(MessageConstant.POST_NOT_EXIST);
        }
        // 他人私密帖对当前用户不可见，自然也不允许收藏（避免通过收藏接口探测私密帖存在性）
        if (Boolean.TRUE.equals(post.getIsPrivate())
                && !Objects.equals(userId, post.getUserId())) {
            return Result.error(MessageConstant.POST_NOT_EXIST);
        }
        if (blacklistUtil.isHidden(userId, post.getUserId())) {
            return Result.error(MessageConstant.POST_NOT_EXIST);
        }
        StarRecord existing = starRecordMapper.selectOne(new LambdaQueryWrapper<StarRecord>()
                .eq(StarRecord::getUserId, userId)
                .eq(StarRecord::getPostId, postId));
        boolean starred;
        if (existing != null) {
            // 取消收藏
            starRecordMapper.deleteById(existing.getId());
            postMapper.update(null, new LambdaUpdateWrapper<Post>()
                    .eq(Post::getId, postId)
                    .setSql("star_count = GREATEST(star_count - 1, 0)"));
            starred = false;
        } else {
            // 收藏
            starRecordMapper.insert(new StarRecord(null, postId, userId, null));
            postMapper.update(null, new LambdaUpdateWrapper<Post>()
                    .eq(Post::getId, postId)
                    .setSql("star_count = star_count + 1"));
            starred = true;
        }
        List<Long> tagIds = tagService.getTagIdsByPostIds(List.of(postId)).getOrDefault(postId, List.of());
        userIntersetScoreUtil.recordPostInteraction(
                userId,
                post.getCategoryId(),
                tagIds,
                starred ? 8 : -8,
                starred ? 10 : -10);
        Integer count = postMapper.selectById(postId).getStarCount();
        return Result.ok(starred ? "收藏成功" : "已取消收藏", count);
    }

    @Override
    public Result listMyStars(Integer pageNum, Integer pageSize) {
        Long userId = userIdUtil.getUserId();
        // 黑名单作者的帖子必须从 SQL 层排除：否则分页 total 仍把它们算进去，
        // 出现「共 N 篇但只列出 N-1 条」的错位
        Set<Long> hidden = blacklistUtil.hiddenUserIds(userId);
        List<Long> blockedPostIds = hidden.isEmpty()
                ? List.of()
                : postMapper.selectList(new LambdaQueryWrapper<Post>()
                        .select(Post::getId)
                        .in(Post::getUserId, hidden))
                .stream().map(Post::getId).toList();
        IPage<StarRecord> page = starRecordMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<StarRecord>()
                        .eq(StarRecord::getUserId, userId)
                        .notIn(!blockedPostIds.isEmpty(), StarRecord::getPostId, blockedPostIds)
                        .orderByDesc(StarRecord::getCreateTime));
        List<Long> postIds = page.getRecords().stream().map(StarRecord::getPostId).toList();
        if (postIds.isEmpty()) {
            return Result.ok(List.of(), page.getTotal());
        }
        // 收藏列表里同样过滤已删除/封禁/私密（他人私密帖）内容
        Map<Long, Post> postMap = postMapper.selectBatchIds(postIds).stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));
        List<Long> userIds = postMap.values().stream()
                .map(Post::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            userMapper.selectBatchIds(userIds).forEach(u -> userMap.put(u.getId(), u));
        }
        Map<Long, Integer> likeCounts = dataCacheUtil.getLikeCounts(postIds);
        Map<Long, Boolean> likedMap = dataCacheUtil.isLikedBatch(userId, postIds);
        List<PostVO> postVOS = new ArrayList<>();
        for (Long postId : postIds) {
            Post post = postMap.get(postId);
            if (post == null || !Boolean.TRUE.equals(post.getEnabled())
                    || !"published".equals(post.getStatus())) {
                continue;
            }
            // 帖子后来设为私密：仅作者自己的收藏列表可见
            if (Boolean.TRUE.equals(post.getIsPrivate()) && !userId.equals(post.getUserId())) {
                continue;
            }
            PostVO postVO = BeanUtil.copyProperties(post, PostVO.class);
            User author = userMap.get(post.getUserId());
            if (author != null) {
                postVO.setUsername(author.getUsername());
                postVO.setNickname(author.getNickname());
            }
            postVO.setLikeCount(likeCounts.getOrDefault(post.getId(), 0));
            postVO.setLiked(likedMap.getOrDefault(post.getId(), false));
            postVO.setStarCount(post.getStarCount() == null ? 0 : post.getStarCount());
            postVO.setStarred(true);
            postVOS.add(postVO);
        }
        return Result.ok(postVOS, page.getTotal());
    }
}
