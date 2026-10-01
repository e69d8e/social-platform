package com.li.socialplatform.common.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 统一的 Redis 缓存操作门面：
 * 1. 业务代码统一注入本类，禁止再直接注入 RedisTemplate
 * 2. 写操作强制携带 TTL，防止缓存永生
 * 3. 批量操作走 pipeline，将 N 次网络往返合并为 1 次
 * 4. 模式匹配统一使用 SCAN，禁止阻塞 Redis 的 KEYS 命令
 *
 * @author e69d8e
 * @since 2026/09/30
 */
@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    // ==================== 各数据类型操作入口 ====================

    public ValueOperations<String, Object> values() {
        return redisTemplate.opsForValue();
    }

    public ZSetOperations<String, Object> zsets() {
        return redisTemplate.opsForZSet();
    }

    public SetOperations<String, Object> sets() {
        return redisTemplate.opsForSet();
    }

    public HashOperations<String, Object, Object> hashes() {
        return redisTemplate.opsForHash();
    }

    public ListOperations<String, Object> lists() {
        return redisTemplate.opsForList();
    }

    /** 字符串模板（限流、自增 ID 等纯字符串场景） */
    public StringRedisTemplate strings() {
        return stringRedisTemplate;
    }

    /** ZSet 批量写入（一次往返） */
    public void zAddAll(String key, Map<Object, Double> memberScores) {
        if (memberScores == null || memberScores.isEmpty()) {
            return;
        }
        Set<ZSetOperations.TypedTuple<Object>> tuples = new HashSet<>(memberScores.size());
        for (Map.Entry<Object, Double> entry : memberScores.entrySet()) {
            tuples.add(ZSetOperations.TypedTuple.of(entry.getKey(), entry.getValue()));
        }
        redisTemplate.opsForZSet().add(key, tuples);
    }

    // ==================== 通用操作 ====================

    /** 写入并强制设置过期时间，缓存不允许无 TTL */
    public void set(String key, Object value, long ttl, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, ttl, unit);
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    public void expire(String key, long ttl, TimeUnit unit) {
        redisTemplate.expire(key, ttl, unit);
    }

    public Long increment(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }

    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    public Long delete(Collection<String> keys) {
        return redisTemplate.delete(keys);
    }

    // ==================== 批量操作（pipeline，一次网络往返） ====================

    /**
     * pipeline 批量执行，回调内多次命令合并为一次网络往返。
     * 注意：回调内只做命令提交，不要读取依赖前一条命令结果的逻辑。
     */
    @SuppressWarnings("unchecked")
    public List<Object> pipeline(Consumer<RedisOperations<String, Object>> callback) {
        return redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) {
                callback.accept(operations);
                return null;
            }
        });
    }

    /** 字符串模板的 pipeline 批量执行 */
    @SuppressWarnings("unchecked")
    public List<Object> pipelineString(Consumer<RedisOperations<String, String>> callback) {
        return stringRedisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) {
                callback.accept(operations);
                return null;
            }
        });
    }

    // ==================== 扫描 ====================

    /** 使用 SCAN 渐进式遍历匹配的 key，代替阻塞的 KEYS 命令 */
    public Set<String> scan(String pattern) {
        Set<String> keys = new HashSet<>();
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(500).build();
            try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return null;
        });
        return keys;
    }
}
