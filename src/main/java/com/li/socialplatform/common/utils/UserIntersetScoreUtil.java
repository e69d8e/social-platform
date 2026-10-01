package com.li.socialplatform.common.utils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.socialplatform.common.constant.CacheTtl;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.pojo.entity.UserInterestScore;
import com.li.socialplatform.server.mapper.UserInterestScoreMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author e69d8e
 * @since 2026/05/23 12:54
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserIntersetScoreUtil {

    private final RedisUtil redisUtil;
    private final UserInterestScoreMapper userInterestScoreMapper;
    private final AsyncTaskUtil asyncTaskUtil;

    public void changeScore(long userId, int categoryId, int score) {
        String key = KeyConstant.USER_INTEREST_SCORE_KEY + userId;
        String field = String.valueOf(categoryId);

        // HINCRBY 原子自增并直接返回新值，避免额外的 HGET 往返
        Long newValue = redisUtil.hashes().increment(key, field, score);
        // 刷新过期时间
        redisUtil.expire(key, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);

        // 异步持久化到 DB
        int totalScore = newValue != null ? newValue.intValue() : score;
        asyncTaskUtil.asyncSaveUserInterestScore(userId, categoryId, totalScore);
    }

    public Map<Integer, Integer> getUserInterestScores(Long userId) {
        String key = KeyConstant.USER_INTEREST_SCORE_KEY + userId;
        String loadedKey = KeyConstant.INTEREST_LOADED_KEY + userId;

        Map<Object, Object> entries = redisUtil.hashes().entries(key);
        if (!entries.isEmpty()) {
            Map<Integer, Integer> scores = new HashMap<>();
            for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                int categoryId = Integer.parseInt(entry.getKey().toString());
                int value = entry.getValue() instanceof Number ? ((Number) entry.getValue()).intValue() : 0;
                scores.put(categoryId, value);
            }
            return scores;
        }

        // 缓存未命中：空分标记存在则不打 DB，否则回源加载
        if (Boolean.TRUE.equals(redisUtil.hasKey(loadedKey))) {
            return new HashMap<>();
        }
        return loadScoresFromDB(userId);
    }

    private Map<Integer, Integer> loadScoresFromDB(Long userId) {
        String key = KeyConstant.USER_INTEREST_SCORE_KEY + userId;
        String loadedKey = KeyConstant.INTEREST_LOADED_KEY + userId;

        List<UserInterestScore> dbScores = userInterestScoreMapper.selectList(
                new LambdaQueryWrapper<UserInterestScore>().eq(UserInterestScore::getUserId, userId));

        Map<Integer, Integer> scores = new HashMap<>();
        if (dbScores.isEmpty()) {
            // 空结果防穿透：短 TTL 标记，避免无兴趣数据的用户每次刷新 Feed 都打 DB
            redisUtil.set(loadedKey, 1, CacheTtl.EMPTY_MARKER_MINUTES, TimeUnit.MINUTES);
            return scores;
        }

        Map<String, Object> hashMap = new HashMap<>();
        for (UserInterestScore us : dbScores) {
            scores.put(us.getCategoryId(), us.getScore());
            hashMap.put(String.valueOf(us.getCategoryId()), us.getScore());
        }

        // 回填 Redis 并设置过期
        redisUtil.hashes().putAll(key, hashMap);
        redisUtil.expire(key, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
        log.debug("从DB加载用户 {} 兴趣分到Redis, 共 {} 条", userId, dbScores.size());

        return scores;
    }
}
