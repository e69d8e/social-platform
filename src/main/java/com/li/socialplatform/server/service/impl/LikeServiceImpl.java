package com.li.socialplatform.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.common.constant.MessageConstant;
import com.li.socialplatform.common.utils.AsyncTaskUtil;
import com.li.socialplatform.common.utils.DataCacheUtil;
import com.li.socialplatform.common.utils.UserIdUtil;
import com.li.socialplatform.common.utils.UserIntersetScoreUtil;
import com.li.socialplatform.server.mapper.LikeMapper;
import com.li.socialplatform.server.mapper.PostMapper;
import com.li.socialplatform.pojo.entity.LikeRecord;
import com.li.socialplatform.pojo.entity.Post;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.server.service.ILikeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * @author e69d8e
 * @since 2025/12/9 21:30
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LikeServiceImpl extends ServiceImpl<LikeMapper, LikeRecord> implements ILikeService {

    private final PostMapper postMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserIdUtil userIdUtil;
    private final AsyncTaskUtil asyncTaskUtil;
    private final UserIntersetScoreUtil userIntersetScoreUtil;
    private final DataCacheUtil dataCacheUtil;

    @Override
    public Result like(Long postId) {
        Long userId = userIdUtil.getUserId();
        String key = KeyConstant.LIKE_KEY + postId;
        // 使用缓存层读取（缓存未命中时自动从 DB 加载）
        boolean member = dataCacheUtil.isLiked(postId, userId);
        Post post = postMapper.selectById(postId);
        Long increment;
        if (member) {
            // 点赞数-1
            increment = redisTemplate.opsForValue().increment(KeyConstant.LIKE_COUNT + postId, -1);
            redisTemplate.opsForSet().remove(key, userId);
            asyncTaskUtil.asyncDeleteLikeRecord(postId, userId);
            if (post != null) {
                userIntersetScoreUtil.changeScore(userId, post.getCategoryId(), -10);
            }
        } else {
            // 点赞数+1
            increment = redisTemplate.opsForValue().increment(KeyConstant.LIKE_COUNT + postId, 1);
            redisTemplate.opsForSet().add(key, userId);
            asyncTaskUtil.asyncInsertLikeRecord(postId, userId);
            if (post != null) {
                userIntersetScoreUtil.changeScore(userId, post.getCategoryId(), 10);
            }
        }
        // 续期 TTL
        dataCacheUtil.setLikeTTL(postId);
        if (increment != null) {
            asyncTaskUtil.syncPostLikeCount(postId, increment.intValue());
        }
        return Result.ok(MessageConstant.LIKE_SUCCESS, "");
    }
}