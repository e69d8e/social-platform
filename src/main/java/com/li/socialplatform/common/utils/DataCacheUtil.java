package com.li.socialplatform.common.utils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.socialplatform.common.constant.CacheTtl;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.pojo.entity.Follow;
import com.li.socialplatform.pojo.entity.LikeRecord;
import com.li.socialplatform.server.mapper.FollowMapper;
import com.li.socialplatform.server.mapper.LikeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 点赞/关注数据的统一缓存组件（Cache-Aside，读时回源 DB 并回填 Redis）
 * - 批量查询走 pipeline，一次网络往返
 * - 空结果通过标记 key 防穿透，避免反复打 DB
 *
 * @author e69d8e
 * @since 2026/05/23
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataCacheUtil {

    private final RedisUtil redisUtil;
    private final LikeMapper likeMapper;
    private final FollowMapper followMapper;

    // ==================== 点赞 ====================

    public boolean isLiked(Long postId, Long userId) {
        if (postId == null || userId == null) {
            return false;
        }
        ensureLikeDataLoaded(postId);
        return Boolean.TRUE.equals(redisUtil.sets().isMember(KeyConstant.LIKE_KEY + postId, userId));
    }

    /**
     * 批量判断用户是否点赞了多个帖子（pipeline 一次往返）
     */
    public Map<Long, Boolean> isLikedBatch(Long userId, Collection<Long> postIds) {
        Map<Long, Boolean> result = new HashMap<>();
        if (userId == null || postIds == null || postIds.isEmpty()) {
            return result;
        }
        List<Long> ids = postIds.stream().filter(Objects::nonNull).distinct().toList();
        // getLikeCounts 内部保证所有帖子的点赞数据已加载（count key 同时是加载标记）
        getLikeCounts(ids);
        List<Object> members = redisUtil.pipeline(ops -> {
            for (Long id : ids) {
                ops.opsForSet().isMember(KeyConstant.LIKE_KEY + id, userId);
            }
        });
        for (int i = 0; i < ids.size(); i++) {
            result.put(ids.get(i), Boolean.TRUE.equals(members.get(i)));
        }
        return result;
    }

    public int getLikeCount(Long postId) {
        if (postId == null) {
            return 0;
        }
        Object value = redisUtil.get(KeyConstant.LIKE_COUNT + postId);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return loadLikeDataFromDB(postId);
    }

    /**
     * 批量获取帖子点赞数（pipeline 一次往返，仅未命中的帖子回源 DB）
     */
    public Map<Long, Integer> getLikeCounts(Collection<Long> postIds) {
        Map<Long, Integer> result = new HashMap<>();
        if (postIds == null || postIds.isEmpty()) {
            return result;
        }
        List<Long> ids = postIds.stream().filter(Objects::nonNull).distinct().toList();
        List<Object> values = redisUtil.pipeline(ops -> {
            for (Long id : ids) {
                ops.opsForValue().get(KeyConstant.LIKE_COUNT + id);
            }
        });
        List<Long> missed = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            Object value = values.get(i);
            if (value instanceof Number number) {
                result.put(ids.get(i), number.intValue());
            } else {
                missed.add(ids.get(i));
            }
        }
        for (Long id : missed) {
            result.put(id, loadLikeDataFromDB(id));
        }
        return result;
    }

    private void ensureLikeDataLoaded(Long postId) {
        // count key 在回源时总是写入（含 0），作为"点赞数据已加载"标记，空数据不重复打 DB
        if (!Boolean.TRUE.equals(redisUtil.hasKey(KeyConstant.LIKE_COUNT + postId))) {
            loadLikeDataFromDB(postId);
        }
    }

    /** 从 DB 加载帖子点赞数据并回填缓存，返回点赞数 */
    private int loadLikeDataFromDB(Long postId) {
        String setKey = KeyConstant.LIKE_KEY + postId;
        String countKey = KeyConstant.LIKE_COUNT + postId;

        List<LikeRecord> records = likeMapper.selectList(
                new LambdaQueryWrapper<LikeRecord>().eq(LikeRecord::getPostId, postId));

        redisUtil.set(countKey, records.size(), CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
        if (!records.isEmpty()) {
            redisUtil.sets().add(setKey, records.stream()
                    .map(r -> (Object) r.getUserId()).distinct().toArray());
            redisUtil.expire(setKey, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
        }
        log.debug("从DB加载帖子 {} 点赞数据: {} 条", postId, records.size());
        return records.size();
    }

    public void setLikeTTL(Long postId) {
        redisUtil.expire(KeyConstant.LIKE_KEY + postId, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
        redisUtil.expire(KeyConstant.LIKE_COUNT + postId, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
    }

    // ==================== 关注 ====================

    public boolean isFollowed(Long userId, Long targetUserId) {
        if (userId == null || targetUserId == null) {
            return false;
        }
        ensureFollowDataLoaded(userId);
        return redisUtil.zsets().score(KeyConstant.Follow_LIST_KEY + userId, targetUserId) != null;
    }

    /**
     * 批量判断当前用户是否关注了多个目标用户（pipeline 一次往返）
     */
    public Map<Long, Boolean> isFollowedBatch(Long userId, Collection<Long> targetUserIds) {
        Map<Long, Boolean> result = new HashMap<>();
        if (userId == null || targetUserIds == null || targetUserIds.isEmpty()) {
            return result;
        }
        List<Long> ids = targetUserIds.stream().filter(Objects::nonNull).distinct().toList();
        ensureFollowDataLoaded(userId);
        List<Object> scores = redisUtil.pipeline(ops -> {
            for (Long id : ids) {
                ops.opsForZSet().score(KeyConstant.Follow_LIST_KEY + userId, id);
            }
        });
        for (int i = 0; i < ids.size(); i++) {
            result.put(ids.get(i), scores.get(i) != null);
        }
        return result;
    }

    public int getFollowerCount(Long userId) {
        if (userId == null) {
            return 0;
        }
        Object value = redisUtil.get(KeyConstant.FOLLOW_COUNT_KEY + userId);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return loadFollowDataFromDB(userId);
    }

    /**
     * 批量获取用户粉丝数（pipeline 一次往返，仅未命中的用户回源 DB）
     */
    public Map<Long, Integer> getFollowerCounts(Collection<Long> userIds) {
        Map<Long, Integer> result = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        List<Object> values = redisUtil.pipeline(ops -> {
            for (Long id : ids) {
                ops.opsForValue().get(KeyConstant.FOLLOW_COUNT_KEY + id);
            }
        });
        List<Long> missed = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            Object value = values.get(i);
            if (value instanceof Number number) {
                result.put(ids.get(i), number.intValue());
            } else {
                missed.add(ids.get(i));
            }
        }
        for (Long id : missed) {
            result.put(id, loadFollowDataFromDB(id));
        }
        return result;
    }

    /**
     * 确保用户关注/粉丝缓存已加载（供列表页等直接读取 ZSet 的场景调用）
     */
    public void ensureFollowDataLoaded(Long userId) {
        if (userId == null) {
            return;
        }
        String listKey = KeyConstant.Follow_LIST_KEY + userId;
        String loadedKey = KeyConstant.FOLLOW_LOADED_KEY + userId;
        if (Boolean.TRUE.equals(redisUtil.hasKey(listKey))
                || Boolean.TRUE.equals(redisUtil.hasKey(loadedKey))) {
            return;
        }
        loadFollowDataFromDB(userId);
    }

    /** 从 DB 加载用户关注/粉丝数据并回填缓存，返回粉丝数 */
    private int loadFollowDataFromDB(Long userId) {
        // 加载粉丝数据（关注 userId 的人）
        List<Follow> fans = followMapper.selectList(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFolloweeId, userId));
        String fansKey = KeyConstant.FANS_LIST_KEY + userId;
        if (!fans.isEmpty()) {
            Map<Object, Double> memberScores = toMemberScores(fans, Follow::getFollowerId);
            redisUtil.zAddAll(fansKey, memberScores);
            redisUtil.expire(fansKey, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
        }
        // 粉丝计数总是写入（含 0），作为粉丝数据的回源结果
        redisUtil.set(KeyConstant.FOLLOW_COUNT_KEY + userId, fans.size(), CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);

        // 加载关注数据（userId 关注的人）
        List<Follow> followees = followMapper.selectList(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFollowerId, userId));
        String followKey = KeyConstant.Follow_LIST_KEY + userId;
        if (!followees.isEmpty()) {
            Map<Object, Double> memberScores = toMemberScores(followees, Follow::getFolloweeId);
            redisUtil.zAddAll(followKey, memberScores);
            redisUtil.expire(followKey, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
        }
        // ZSet 无法表达空集合，用短 TTL 标记防止"关注了 0 人"的用户反复打 DB
        redisUtil.set(KeyConstant.FOLLOW_LOADED_KEY + userId, 1,
                CacheTtl.EMPTY_MARKER_MINUTES, TimeUnit.MINUTES);

        log.debug("从DB加载用户 {} 关注/粉丝数据: {} 粉丝, {} 关注", userId, fans.size(), followees.size());
        return fans.size();
    }

    private Map<Object, Double> toMemberScores(List<Follow> follows,
                                               java.util.function.Function<Follow, Long> memberIdGetter) {
        return follows.stream().collect(Collectors.toMap(
                f -> (Object) memberIdGetter.apply(f),
                f -> f.getCreateTime() != null
                        ? f.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        : (double) System.currentTimeMillis(),
                (a, b) -> a));
    }

    public void setFollowTTL(Long userId, Long targetUserId) {
        redisUtil.expire(KeyConstant.FOLLOW_COUNT_KEY + targetUserId, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
        redisUtil.expire(KeyConstant.FANS_LIST_KEY + targetUserId, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
        redisUtil.expire(KeyConstant.Follow_LIST_KEY + userId, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
    }
}
