package com.li.socialplatform.filter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.socialplatform.common.constant.CacheTtl;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.common.utils.JwtUtils;
import com.li.socialplatform.common.utils.RedisUtil;
import com.li.socialplatform.common.utils.TokenCacheUtil;
import com.li.socialplatform.pojo.entity.Authority;
import com.li.socialplatform.pojo.entity.User;
import com.li.socialplatform.server.mapper.AuthorityMapper;
import com.li.socialplatform.server.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 * @author e69d8e
 * @since 2026/05/10 15:14
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserMapper userMapper;
    private final AuthorityMapper authorityMapper;
    private final RedisUtil redisUtil;
    private final TokenCacheUtil tokenCacheUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NotNull HttpServletResponse response,
                                    @NotNull FilterChain chain) throws IOException, ServletException {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            try {
                Claims claims = jwtUtils.parseToken(token);
                String username = claims.getSubject();
                log.debug("Token 解析成功, 用户名: {}", username);

                // 检查 token 是否在黑名单中（已注销）
                String jti = claims.getId();
                if (jti != null && tokenCacheUtil.isBlacklisted(jti)) {
                    log.debug("Token 已被注销, 用户名: {}", username);
                    request.setAttribute("TOKEN_BLACKLISTED", true);
                    chain.doFilter(request, response);
                    return;
                }

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    String role = getCachedUserRole(username);
                    if (role != null) {
                        String roleWithPrefix = role.startsWith("ROLE_") ? role : "ROLE_" + role;
                        List<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority(roleWithPrefix));
                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(username, null, authorities);
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    } else {
                        log.warn("用户不存在或角色查询失败: {}", username);
                    }
                }
            } catch (JwtException e) {
                log.debug("Token 解析失败，放行请求: {}", e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * 从 Redis 缓存获取用户角色，缓存未命中时查 DB 并回填
     */
    private String getCachedUserRole(String username) {
        String cacheKey = KeyConstant.USER_ROLE_KEY + username;
        Object cached = redisUtil.get(cacheKey);
        if (cached != null) {
            return cached.toString();
        }
        // 缓存未命中，查 DB
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            return null;
        }
        Authority authority = authorityMapper.selectById(user.getAuthorityId());
        if (authority == null) {
            return null;
        }
        String role = authority.getAuthority();
        redisUtil.set(cacheKey, role, CacheTtl.USER_ROLE_MINUTES, TimeUnit.MINUTES);
        return role;
    }
}
