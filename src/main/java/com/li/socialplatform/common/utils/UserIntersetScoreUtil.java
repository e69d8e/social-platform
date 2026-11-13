package com.li.socialplatform.common.utils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.socialplatform.common.constant.CacheTtl;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.pojo.entity.UserInterestScore;
import com.li.socialplatform.pojo.entity.UserTagInterest;
import com.li.socialplatform.server.mapper.UserInterestScoreMapper;
import com.li.socialplatform.server.mapper.UserTagInterestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 用户兴趣画像工具：
 * <ul>
 *   <li>维护分类与标签两级兴趣分（Redis Hash + 异步落库 MySQL）</li>
 *   <li>支持浏览兴趣防刷冷却（5 分钟去重）与分值上下界钳位 [0, 1000]</li>
 *   <li>读取画像时应用 14 天半衰期的指数时间衰减，避免陈旧兴趣长期固化</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class UserIntersetScoreUtil {

    public static final int MAX_INTEREST_SCORE = 1000;
    public static final double DEFAULT_HALF_LIFE_DAYS = 14.0;

    private final RedisUtil redisUtil;
    private final UserInterestScoreMapper userInterestScoreMapper;
    private final UserTagInterestMapper userTagInterestMapper;
    private final InterestAsyncWriter interestAsyncWriter;

    /**
     * 指数时间衰减公式（半衰期模型）
     */
    public static double applyTimeDecay(int rawScore, long updatedEpochSeconds, long nowEpochSeconds, double halfLifeDays) {
        if (rawScore <= 0) {
            return 0.0;
        }
        double safeHalfLife = halfLifeDays <= 0 ? DEFAULT_HALF_LIFE_DAYS : halfLifeDays;
        double ageDays = Math.max(0.0, (nowEpochSeconds - updatedEpochSeconds) / 86400.0);
        double decay = Math.exp(-Math.log(2.0) * ageDays / safeHalfLife);
        return rawScore * decay;
    }

    /**
     * 记录浏览带来的兴趣增量：同一用户 5 分钟内重复打开同一帖子只加一次
     */
    public void recordViewWithCooldown(Long userId, Long postId, Integer categoryId, Collection<Long> tagIds) {
        if (userId == null || postId == null) {
            return;
        }
        String cooldownKey = KeyConstant.INTEREST_VIEW_COOLDOWN_KEY + userId + ":" + postId;
        Boolean first = redisUtil.values().setIfAbsent(
                cooldownKey, 1, CacheTtl.INTEREST_VIEW_COOLDOWN_MINUTES, TimeUnit.MINUTES);
        if (Boolean.TRUE.equals(first)) {
            recordPostInteraction(userId, categoryId, tagIds, 1, 2);
        }
    }

    /**
     * 统一记录帖子互动（点赞/收藏/评论/发帖/负反馈）对分类和标签兴趣的影响
     */
    public void recordPostInteraction(
            Long userId,
            Integer categoryId,
            Collection<Long> tagIds,
            int categoryDelta,
            int tagDelta) {
        if (userId == null) {
            return;
        }
        if (categoryId != null && categoryDelta != 0) {
            changeScore(userId, categoryId, categoryDelta);
        }
        if (tagIds != null && !tagIds.isEmpty() && tagDelta != 0) {
            changeTagScores(userId, tagIds, tagDelta);
        }
    }

    /**
     * 变更用户分类兴趣分（钳位在 [0, 1000] 并刷新时间戳）
     */
    public void changeScore(long userId, int categoryId, int delta) {
        ensureCategoryScoresLoaded(userId);
        String scoreKey = KeyConstant.USER_INTEREST_SCORE_KEY + userId;
        String tsKey = KeyConstant.USER_INTEREST_TS_KEY + userId;
        String field = String.valueOf(categoryId);

        Object rawObj = redisUtil.hashes().get(scoreKey, field);
        int current = rawObj == null ? 0 : Math.max(0, Integer.parseInt(rawObj.toString()));
        int next = clampScore(current + delta);

        long nowEpoch = Instant.now().getEpochSecond();
        redisUtil.hashes().put(scoreKey, field, next);
        redisUtil.hashes().put(tsKey, field, nowEpoch);
        redisUtil.expire(scoreKey, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
        redisUtil.expire(tsKey, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
        redisUtil.expire(KeyConstant.INTEREST_LOADED_KEY + userId, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);

        interestAsyncWriter.saveCategoryScore(userId, categoryId, next);
    }

    /**
     * 批量变更用户标签兴趣分
     */
    public void changeTagScores(long userId, Collection<Long> tagIds, int delta) {
        if (tagIds == null || tagIds.isEmpty() || delta == 0) {
            return;
        }
        ensureTagScoresLoaded(userId);
        String scoreKey = KeyConstant.USER_TAG_INTEREST_KEY + userId;
        String tsKey = KeyConstant.USER_TAG_INTEREST_TS_KEY + userId;
        long nowEpoch = Instant.now().getEpochSecond();

        for (Long tagId : tagIds) {
            if (tagId == null) {
                continue;
            }
            String field = String.valueOf(tagId);
            Object rawObj = redisUtil.hashes().get(scoreKey, field);
            int current = rawObj == null ? 0 : Math.max(0, Integer.parseInt(rawObj.toString()));
            int next = clampScore(current + delta);
            redisUtil.hashes().put(scoreKey, field, next);
            redisUtil.hashes().put(tsKey, field, nowEpoch);
            interestAsyncWriter.saveTagScore(userId, tagId, next);
        }
        redisUtil.expire(scoreKey, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
        redisUtil.expire(tsKey, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
        redisUtil.expire(KeyConstant.USER_TAG_INTEREST_LOADED_KEY + userId, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
    }

    /**
     * 清零某个分类或标签的正向兴趣（供「减少同类内容推荐」负反馈调用）
     */
    public void resetCategoryScore(Long userId, Integer categoryId) {
        if (userId == null || categoryId == null) {
            return;
        }
        ensureCategoryScoresLoaded(userId);
        String scoreKey = KeyConstant.USER_INTEREST_SCORE_KEY + userId;
        String tsKey = KeyConstant.USER_INTEREST_TS_KEY + userId;
        String field = String.valueOf(categoryId);
        redisUtil.hashes().put(scoreKey, field, 0);
        redisUtil.hashes().put(tsKey, field, Instant.now().getEpochSecond());
        interestAsyncWriter.saveCategoryScore(userId, categoryId, 0);
    }

    public void resetTagScore(Long userId, Long tagId) {
        if (userId == null || tagId == null) {
            return;
        }
        ensureTagScoresLoaded(userId);
        String scoreKey = KeyConstant.USER_TAG_INTEREST_KEY + userId;
        String tsKey = KeyConstant.USER_TAG_INTEREST_TS_KEY + userId;
        String field = String.valueOf(tagId);
        redisUtil.hashes().put(scoreKey, field, 0);
        redisUtil.hashes().put(tsKey, field, Instant.now().getEpochSecond());
        interestAsyncWriter.saveTagScore(userId, tagId, 0);
    }

    /**
     * 获取用户原始正向分类兴趣分（向后兼容接口，过滤非正分）
     */
    public Map<Integer, Integer> getUserInterestScores(Long userId) {
        if (userId == null) {
            return Map.of();
        }
        ensureCategoryScoresLoaded(userId);
        String key = KeyConstant.USER_INTEREST_SCORE_KEY + userId;
        Map<Object, Object> entries = redisUtil.hashes().entries(key);
        Map<Integer, Integer> scores = new HashMap<>();
        if (entries != null) {
            for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                int catId = Integer.parseInt(entry.getKey().toString());
                int val = Integer.parseInt(entry.getValue().toString());
                if (val > 0) {
                    scores.put(catId, val);
                }
            }
        }
        return scores;
    }

    /**
     * 获取应用 14 天半衰期时间衰减后的分类兴趣分
     */
    public Map<Integer, Double> getDecayedCategoryScores(Long userId) {
        if (userId == null) {
            return Map.of();
        }
        ensureCategoryScoresLoaded(userId);
        String scoreKey = KeyConstant.USER_INTEREST_SCORE_KEY + userId;
        String tsKey = KeyConstant.USER_INTEREST_TS_KEY + userId;
        Map<Object, Object> scoreEntries = redisUtil.hashes().entries(scoreKey);
        if (scoreEntries == null || scoreEntries.isEmpty()) {
            return Map.of();
        }
        Map<Object, Object> tsEntries = redisUtil.hashes().entries(tsKey);
        long nowEpoch = Instant.now().getEpochSecond();

        Map<Integer, Double> decayed = new HashMap<>();
        for (Map.Entry<Object, Object> entry : scoreEntries.entrySet()) {
            String field = entry.getKey().toString();
            int raw = Integer.parseInt(entry.getValue().toString());
            if (raw <= 0) {
                continue;
            }
            long updatedEpoch = parseEpoch(tsEntries == null ? null : tsEntries.get(field), nowEpoch);
            double score = applyTimeDecay(raw, updatedEpoch, nowEpoch, DEFAULT_HALF_LIFE_DAYS);
            if (score > 0.05) {
                decayed.put(Integer.parseInt(field), score);
            }
        }
        return decayed;
    }

    /**
     * 获取应用 14 天半衰期时间衰减后的标签兴趣分
     */
    public Map<Long, Double> getDecayedTagScores(Long userId) {
        if (userId == null) {
            return Map.of();
        }
        ensureTagScoresLoaded(userId);
        String scoreKey = KeyConstant.USER_TAG_INTEREST_KEY + userId;
        String tsKey = KeyConstant.USER_TAG_INTEREST_TS_KEY + userId;
        Map<Object, Object> scoreEntries = redisUtil.hashes().entries(scoreKey);
        if (scoreEntries == null || scoreEntries.isEmpty()) {
            return Map.of();
        }
        Map<Object, Object> tsEntries = redisUtil.hashes().entries(tsKey);
        long nowEpoch = Instant.now().getEpochSecond();

        Map<Long, Double> decayed = new HashMap<>();
        for (Map.Entry<Object, Object> entry : scoreEntries.entrySet()) {
            String field = entry.getKey().toString();
            int raw = Integer.parseInt(entry.getValue().toString());
            if (raw <= 0) {
                continue;
            }
            long updatedEpoch = parseEpoch(tsEntries == null ? null : tsEntries.get(field), nowEpoch);
            double score = applyTimeDecay(raw, updatedEpoch, nowEpoch, DEFAULT_HALF_LIFE_DAYS);
            if (score > 0.05) {
                decayed.put(Long.parseLong(field), score);
            }
        }
        return decayed;
    }

    private void ensureCategoryScoresLoaded(Long userId) {
        String loadedKey = KeyConstant.INTEREST_LOADED_KEY + userId;
        if (Boolean.TRUE.equals(redisUtil.hasKey(loadedKey))) {
            return;
        }
        List<UserInterestScore> rows = userInterestScoreMapper.selectList(
                new LambdaQueryWrapper<UserInterestScore>().eq(UserInterestScore::getUserId, userId));
        String scoreKey = KeyConstant.USER_INTEREST_SCORE_KEY + userId;
        String tsKey = KeyConstant.USER_INTEREST_TS_KEY + userId;
        if (!rows.isEmpty()) {
            Map<Object, Object> scoreMap = new HashMap<>();
            Map<Object, Object> tsMap = new HashMap<>();
            long nowEpoch = Instant.now().getEpochSecond();
            for (UserInterestScore row : rows) {
                String field = String.valueOf(row.getCategoryId());
                scoreMap.put(field, clampScore(row.getScore() == null ? 0 : row.getScore()));
                tsMap.put(field, toEpochSeconds(row.getUpdateTime(), nowEpoch));
            }
            redisUtil.hashes().putAll(scoreKey, scoreMap);
            redisUtil.hashes().putAll(tsKey, tsMap);
            redisUtil.expire(scoreKey, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
            redisUtil.expire(tsKey, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
        }
        redisUtil.set(loadedKey, 1, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
    }

    private void ensureTagScoresLoaded(Long userId) {
        String loadedKey = KeyConstant.USER_TAG_INTEREST_LOADED_KEY + userId;
        if (Boolean.TRUE.equals(redisUtil.hasKey(loadedKey))) {
            return;
        }
        List<UserTagInterest> rows = userTagInterestMapper.selectList(
                new LambdaQueryWrapper<UserTagInterest>().eq(UserTagInterest::getUserId, userId));
        String scoreKey = KeyConstant.USER_TAG_INTEREST_KEY + userId;
        String tsKey = KeyConstant.USER_TAG_INTEREST_TS_KEY + userId;
        if (!rows.isEmpty()) {
            Map<Object, Object> scoreMap = new HashMap<>();
            Map<Object, Object> tsMap = new HashMap<>();
            long nowEpoch = Instant.now().getEpochSecond();
            for (UserTagInterest row : rows) {
                String field = String.valueOf(row.getTagId());
                scoreMap.put(field, clampScore(row.getScore() == null ? 0 : row.getScore()));
                tsMap.put(field, toEpochSeconds(row.getUpdateTime(), nowEpoch));
            }
            redisUtil.hashes().putAll(scoreKey, scoreMap);
            redisUtil.hashes().putAll(tsKey, tsMap);
            redisUtil.expire(scoreKey, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
            redisUtil.expire(tsKey, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
        }
        redisUtil.set(loadedKey, 1, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
    }

    private static int clampScore(int val) {
        return Math.max(0, Math.min(MAX_INTEREST_SCORE, val));
    }

    private static long toEpochSeconds(LocalDateTime time, long fallback) {
        if (time == null) {
            return fallback;
        }
        return time.atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    private static long parseEpoch(Object raw, long fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Long.parseLong(raw.toString());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
