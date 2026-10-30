package com.li.socialplatform.server.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.socialplatform.common.constant.MessageConstant;
import com.li.socialplatform.common.utils.DataCacheUtil;
import com.li.socialplatform.common.utils.UserIdUtil;
import com.li.socialplatform.pojo.entity.Post;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.pojo.entity.StarRecord;
import com.li.socialplatform.pojo.entity.User;
import com.li.socialplatform.pojo.vo.PostVO;
import com.li.socialplatform.server.mapper.PostMapper;
import com.li.socialplatform.server.mapper.StarRecordMapper;
import com.li.socialplatform.server.mapper.UserMapper;
import com.li.socialplatform.server.service.IStarService;
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

    @Transactional
    @Override
    public Result star(Long postId) {
        Long userId = userIdUtil.getUserId();
        Post post = postMapper.selectById(postId);
        if (post == null || !Boolean.TRUE.equals(post.getEnabled())) {
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
        Integer count = postMapper.selectById(postId).getStarCount();
        return Result.ok(starred ? "收藏成功" : "已取消收藏", count);
    }

    @Override
    public Result listMyStars(Integer pageNum, Integer pageSize) {
        Long userId = userIdUtil.getUserId();
        IPage<StarRecord> page = starRecordMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<StarRecord>()
                        .eq(StarRecord::getUserId, userId)
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
            if (post == null || !Boolean.TRUE.equals(post.getEnabled())) {
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
