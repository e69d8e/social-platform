package com.li.socialplatform.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.common.utils.JwtUtils;
import com.li.socialplatform.pojo.entity.User;
import com.li.socialplatform.server.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.Collections;
import java.util.List;

/**
 * @author e69d8e
 * @since 2025/12/27
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtUtils jwtUtils;
    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${cors.allowed-origin:http://127.0.0.1:5173}")
    private String allowedOrigin;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用简单内存消息代理，前端订阅 /user/queue/messages 接收私信
        config.enableSimpleBroker("/queue");
        // 用户目标前缀
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigin)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(@NotNull Message<?> message, @NotNull MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    // 从 STOMP CONNECT headers 中获取 token
                    List<String> authorization = accessor.getNativeHeader("token");
                    if (authorization != null && !authorization.isEmpty()) {
                        String token = authorization.getFirst();
                        try {
                            Claims claims = jwtUtils.parseToken(token);
                            String username = claims.getSubject();
                            // 检查 token 是否在黑名单中
                            String jti = claims.getId();
                            if (jti != null && redisTemplate.hasKey(KeyConstant.TOKEN_BLACKLIST_KEY + jti)) {
                                log.warn("WebSocket CONNECT 拒绝: token 已注销");
                                return null;
                            }
                            if (username != null) {
                                User user = userMapper.selectOne(
                                        new LambdaQueryWrapper<User>().eq(User::getUsername, username));
                                if (user != null) {
                                    Principal principal = new UsernamePasswordAuthenticationToken(
                                            String.valueOf(user.getId()), null, Collections.emptyList());
                                    accessor.setUser(principal);
                                    log.info("WebSocket 用户连接: userId={}, username={}", user.getId(), username);
                                }
                            }
                        } catch (Exception e) {
                            log.warn("WebSocket CONNECT token 验证失败: {}", e.getMessage());
                            return null;
                        }
                    }
                }
                return message;
            }
        });
    }
}
