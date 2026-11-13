package com.li.socialplatform.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.common.constant.MessageConstant;
import com.li.socialplatform.common.utils.AsyncTaskUtil;
import com.li.socialplatform.common.utils.BlacklistUtil;
import com.li.socialplatform.common.utils.DataCacheUtil;
import com.li.socialplatform.common.utils.RedisUtil;
import com.li.socialplatform.common.utils.UserIdUtil;
import com.li.socialplatform.common.utils.UserIntersetScoreUtil;
import com.li.socialplatform.server.mapper.LikeMapper;
import com.li.socialplatform.server.mapper.PostMapper;
import com.li.socialplatform.pojo.entity.LikeRecord;
import com.li.socialplatform.pojo.entity.Post;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.server.service.ILikeService;
import com.li.socialplatform.server.service.ITagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * @author e69d8e
 * @since 2025/12/9 21:30
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LikeServiceImpl extends ServiceImpl<LikeMapper, LikeRecord> implements ILikeService {

    private final PostMapper postMapper;
    private final RedisUtil redisUtil;
    private final UserIdUtil userIdUtil;
    private final AsyncTaskUtil asyncTaskUtil;
    private final UserIntersetScoreUtil userIntersetScoreUtil;
    private final DataCacheUtil dataCacheUtil;
    private final BlacklistUtil blacklistUtil;
    private final ITagService tagService;

    @Override
    public Result like(Long postId) {
        Long userId = userIdUtil.getUserId();
        String key = KeyConstant.LIKE_KEY + postId;
        // 使用缓存层读取（缓存未命中时自动从 DB 加载）
        boolean member = dataCacheUtil.isLiked(postId, userId);
        Post post = postMapper.selectById(postId);
        if (post == null || !Boolean.TRUE.equals(post.getEnabled())) {
            return Result.error(MessageConstant.POST_NOT_EXIST);
        }
        // 他人私密帖不可见，也就不能点赞
        if (Boolean.TRUE.equals(post.getIsPrivate())
                && !Objects.equals(userId, post.getUserId())) {
            return Result.error(MessageConstant.POST_NOT_EXIST);
        }
        // 黑名单：互不可见的内容也不允许交互（返回同一文案，避免成为探测信号）
        if (blacklistUtil.isHidden(userId, post.getUserId())) {
            return Result.error(MessageConstant.POST_NOT_EXIST);
        }
        Long increment;
        List<Long> tagIds = post != null
                ? tagService.getTagIdsByPostIds(List.of(postId)).getOrDefault(postId, List.of())
                : List.of();
        if (member) {
            // 点赞数-1
            increment = redisUtil.increment(KeyConstant.LIKE_COUNT + postId, -1);
            redisUtil.sets().remove(key, userId);
            asyncTaskUtil.asyncDeleteLikeRecord(postId, userId);
            if (post != null) {
                userIntersetScoreUtil.recordPostInteraction(userId, post.getCategoryId(), tagIds, -10, -12);
            }
        } else {
            // 点赞数+1
            increment = redisUtil.increment(KeyConstant.LIKE_COUNT + postId, 1);
            redisUtil.sets().add(key, userId);
            asyncTaskUtil.asyncInsertLikeRecord(postId, userId);
            if (post != null) {
                userIntersetScoreUtil.recordPostInteraction(userId, post.getCategoryId(), tagIds, 10, 12);
            }
        }
        // 续期 TTL
        dataCacheUtil.setLikeTTL(postId);
        if (increment != null) {
            asyncTaskUtil.syncPostLikeCount(postId, increment.intValue());
        }
        return Result.ok(member ? MessageConstant.UN_LIKE_SUCCESS : MessageConstant.LIKE_SUCCESS, "");
    }
}