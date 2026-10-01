package com.li.socialplatform.common.utils;

import com.li.socialplatform.common.constant.KeyConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 统一管理 refreshToken 与 accessToken 黑名单的缓存读写，
 * 调用方：登录/登出/刷新（UserServiceImpl）、HTTP 过滤器、WebSocket 握手
 *
 * @author e69d8e
 * @since 2026/09/30
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TokenCacheUtil {

    private final RedisUtil redisUtil;

    // ==================== refreshToken ====================

    public void setRefreshToken(String username, String refreshToken, long ttl, TimeUnit unit) {
        redisUtil.set(KeyConstant.REFRESH_KEY + username, refreshToken, ttl, unit);
    }

    public String getRefreshToken(String username) {
        Object value = redisUtil.get(KeyConstant.REFRESH_KEY + username);
        return value != null ? value.toString() : null;
    }

    public void deleteRefreshToken(String username) {
        redisUtil.delete(KeyConstant.REFRESH_KEY + username);
    }

    // ==================== accessToken 黑名单 ====================

    /** 将 access token 加入黑名单，TTL 与 token 剩余有效期一致，到期自动清理 */
    public void blacklistToken(String jti, long remainingMillis) {
        redisUtil.set(KeyConstant.TOKEN_BLACKLIST_KEY + jti, "", remainingMillis, TimeUnit.MILLISECONDS);
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisUtil.hasKey(KeyConstant.TOKEN_BLACKLIST_KEY + jti));
    }
}
