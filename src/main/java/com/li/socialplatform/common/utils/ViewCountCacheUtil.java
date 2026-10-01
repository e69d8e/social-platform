package com.li.socialplatform.common.utils;

import com.li.socialplatform.common.constant.CacheTtl;
import com.li.socialplatform.common.constant.KeyConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 帖子浏览量缓存：Redis 记录待同步增量，定时任务批量落库。
 * 写入方：recordView（去重冷却 + 自增）；读取方：帖子详情、定时同步任务
 *
 * @author e69d8e
 * @since 2026/09/30
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ViewCountCacheUtil {

    private final RedisUtil redisUtil;

    /**
     * 记录一次浏览：同一用户/IP 对同一帖子冷却期内只计一次。
     *
     * @return true 表示本次为有效浏览（冷却通过并已自增计数）
     */
    public boolean recordView(Long postId, String identifier) {
        String cooldownKey = KeyConstant.POST_VIEW_COOLDOWN + identifier + ":" + postId;
        // setIfAbsent 原子操作，避免 hasKey + set 之间的竞态条件
        Boolean acquired = redisUtil.values().setIfAbsent(cooldownKey, 1, CacheTtl.VIEW_COOLDOWN_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(acquired)) {
            return false;
        }
        redisUtil.increment(KeyConstant.POST_VIEW_COUNT + postId, 1);
        return true;
    }

    /** 读取帖子在 Redis 中尚未落库的浏览量增量 */
    public long getViewDelta(Long postId) {
        Object value = redisUtil.get(KeyConstant.POST_VIEW_COUNT + postId);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    /** 批量读取多个浏览量 key 的值（value 为待落库的增量；无法解析的脏数据按 0 返回以便调用方清理） */
    public Map<Long, Long> batchGetDeltas(Set<String> keys) {
        List<Object> results = redisUtil.pipeline(ops -> {
            for (String key : keys) {
                ops.opsForValue().get(key);
            }
        });
        Map<Long, Long> deltas = new HashMap<>(keys.size());
        int index = 0;
        for (String key : keys) {
            Object value = results.get(index++);
            Long postId = parsePostId(key);
            if (postId == null) {
                log.warn("跳过无法解析的浏览量 key: {}", key);
                continue;
            }
            deltas.put(postId, value instanceof Number number ? number.longValue() : 0L);
        }
        return deltas;
    }

    /** 删除已同步完成的浏览量 key */
    public void deleteKeys(List<String> keys) {
        if (keys != null && !keys.isEmpty()) {
            redisUtil.delete(keys);
        }
    }

    /** SCAN 遍历所有待同步的浏览量 key（代替阻塞的 KEYS 命令） */
    public Set<String> scanPendingKeys() {
        return redisUtil.scan(KeyConstant.POST_VIEW_COUNT + "*");
    }

    private Long parsePostId(String key) {
        try {
            return Long.parseLong(key.substring(KeyConstant.POST_VIEW_COUNT.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
