package com.li.socialplatform.common.utils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.pojo.entity.Follow;
import com.li.socialplatform.pojo.entity.LikeRecord;
import com.li.socialplatform.server.mapper.FollowMapper;
import com.li.socialplatform.server.mapper.LikeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author e69d8e
 * @since 2026/05/23
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataCacheUtil {

    private final RedisTemplate<String, Object> redisTemplate;
    private final LikeMapper likeMapper;
    private final FollowMapper followMapper;

    private static final long CACHE_TTL_DAYS = 7;

    // ==================== 点赞 ====================

    public boolean isLiked(Long postId, Long userId) {
        if (postId == null || userId == null) {
            return false;
        }
        String key = KeyConstant.LIKE_KEY + postId;
        Boolean member = redisTemplate.opsForSet().isMember(key, userId);
        if (member != null) {
            return member;
        }
        // 缓存未命中，从 DB 加载
        loadLikeDataFromDB(postId);
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, userId));
    }

    public int getLikeCount(Long postId) {
        String key = KeyConstant.LIKE_COUNT + postId;
        Object value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            return value instanceof Number ? ((Number) value).intValue() : 0;
        }
        // 缓存未命中，从 DB 加载
        loadLikeDataFromDB(postId);
        Object reloaded = redisTemplate.opsForValue().get(key);
        return reloaded instanceof Number ? ((Number) reloaded).intValue() : 0;
    }

    private void loadLikeDataFromDB(Long postId) {
        String setKey = KeyConstant.LIKE_KEY + postId;
        String countKey = KeyConstant.LIKE_COUNT + postId;

        List<LikeRecord> records = likeMapper.selectList(
                new LambdaQueryWrapper<LikeRecord>().eq(LikeRecord::getPostId, postId));

        if (records.isEmpty()) {
            // 标记空缓存，防止缓存穿透
            redisTemplate.opsForValue().set(countKey, 0, CACHE_TTL_DAYS, TimeUnit.DAYS);
            return;
        }

        redisTemplate.opsForSet().add(setKey, records.stream()
                .map(r -> (Object) r.getUserId()).distinct().toArray());
        redisTemplate.expire(setKey, CACHE_TTL_DAYS, TimeUnit.DAYS);
        redisTemplate.opsForValue().set(countKey, records.size(), CACHE_TTL_DAYS, TimeUnit.DAYS);
        log.debug("从DB加载帖子 {} 点赞数据: {} 条", postId, records.size());
    }

    public void setLikeTTL(Long postId) {
        redisTemplate.expire(KeyConstant.LIKE_KEY + postId, CACHE_TTL_DAYS, TimeUnit.DAYS);
        redisTemplate.expire(KeyConstant.LIKE_COUNT + postId, CACHE_TTL_DAYS, TimeUnit.DAYS);
    }

    // ==================== 关注 ====================

    public boolean isFollowed(Long userId, Long targetUserId) {
        if (userId == null || targetUserId == null) {
            return false;
        }
        Double score = redisTemplate.opsForZSet().score(KeyConstant.Follow_LIST_KEY + userId, targetUserId);
        if (score != null) {
            return true;
        }
        // 缓存未命中，从 DB 加载
        loadFollowDataFromDB(userId, targetUserId);
        return redisTemplate.opsForZSet().score(KeyConstant.Follow_LIST_KEY + userId, targetUserId) != null;
    }

    public int getFollowerCount(Long userId) {
        Object value = redisTemplate.opsForValue().get(KeyConstant.FOLLOW_COUNT_KEY + userId);
        if (value != null) {
            return value instanceof Number ? ((Number) value).intValue() : 0;
        }
        loadFollowDataFromDB(userId, null);
        Object reloaded = redisTemplate.opsForValue().get(KeyConstant.FOLLOW_COUNT_KEY + userId);
        return reloaded instanceof Number ? ((Number) reloaded).intValue() : 0;
    }

    private void loadFollowDataFromDB(Long userId, Long targetUserId) {
        // 加载粉丝数据（关注 userId 的人）
        List<Follow> fans = followMapper.selectList(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFolloweeId, userId));
        if (!fans.isEmpty()) {
            String fansKey = KeyConstant.FANS_LIST_KEY + userId;
            for (Follow f : fans) {
                double score = f.getCreateTime() != null
                        ? f.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        : (double) System.currentTimeMillis();
                redisTemplate.opsForZSet().add(fansKey, f.getFollowerId(), score);
            }
            redisTemplate.expire(fansKey, CACHE_TTL_DAYS, TimeUnit.DAYS);
            redisTemplate.opsForValue().set(KeyConstant.FOLLOW_COUNT_KEY + userId, fans.size(), CACHE_TTL_DAYS, TimeUnit.DAYS);
        } else {
            redisTemplate.opsForValue().set(KeyConstant.FOLLOW_COUNT_KEY + userId, 0, CACHE_TTL_DAYS, TimeUnit.DAYS);
        }

        // 加载关注数据（userId 关注的人）
        List<Follow> followees = followMapper.selectList(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFollowerId, userId));
        if (!followees.isEmpty()) {
            String followKey = KeyConstant.Follow_LIST_KEY + userId;
            for (Follow f : followees) {
                double score = f.getCreateTime() != null
                        ? f.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        : (double) System.currentTimeMillis();
                redisTemplate.opsForZSet().add(followKey, f.getFolloweeId(), score);
            }
            redisTemplate.expire(followKey, CACHE_TTL_DAYS, TimeUnit.DAYS);
        }

        log.debug("从DB加载用户 {} 关注/粉丝数据: {} 粉丝, {} 关注", userId, fans.size(), followees.size());
    }

    public void setFollowTTL(Long userId, Long targetUserId) {
        redisTemplate.expire(KeyConstant.FOLLOW_COUNT_KEY + targetUserId, CACHE_TTL_DAYS, TimeUnit.DAYS);
        redisTemplate.expire(KeyConstant.FANS_LIST_KEY + targetUserId, CACHE_TTL_DAYS, TimeUnit.DAYS);
        redisTemplate.expire(KeyConstant.Follow_LIST_KEY + userId, CACHE_TTL_DAYS, TimeUnit.DAYS);
    }
}
