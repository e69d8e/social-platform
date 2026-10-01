package com.li.socialplatform.common.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisIdUtils { // redis id 生成器

    private static final long BEGIN_TIMESTAMP = 1735689600L;
    private final RedisUtil redisUtil;
    private static final long COUNT_BITS = 32;

    public Long nextId(String key) {
        // 获取当前时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowTimestamp = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowTimestamp - BEGIN_TIMESTAMP;
        // 生成序列号
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String redisKey = "icr:" + key + date;
        Long count = redisUtil.strings().opsForValue().increment(redisKey);
        // 首次写入时设置过期，避免按天分片的 key 永久残留
        if (count != null && count == 1) {
            redisUtil.strings().expire(redisKey, 2, TimeUnit.DAYS);
        }
        // 拼接并返回
        if (count != null) {
            return timeStamp << COUNT_BITS | count;
        }
        return null;
    }
}
