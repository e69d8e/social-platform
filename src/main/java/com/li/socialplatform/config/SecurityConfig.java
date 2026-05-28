package com.li.socialplatform.config;

import com.li.socialplatform.filter.JwtAuthenticationFilter;
import com.li.socialplatform.handler.*;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity // 开启方法权限控制
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        //authorizeRequests()：开启授权保护
        //anyRequest()：对所有请求开启授权保护
        //authenticated()：已认证请求会自动被授权
        http.authorizeHttpRequests(
                        authorize ->
                                authorize
                                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                                        .requestMatchers(
                                                "/v3/api-docs/**",
                                                "/swagger-ui/**",
                                                "/swagger-ui.html",
                                                "/swagger-resources/**",
                                                "/doc.html",
                                                "/webjars/**",
                                                "/user/login", // 登录接口
                                                "/user/refresh", // 刷新token接口
                                                "/user/register", // 注册接口
                                                "/test2", // 测试接口
                                                "/user/profile/*", // 查询其他用户信息接口
                                                "/follow/list/followee/{id}", // 其他用户关注列表接口
                                                "/follow/list/{id}", // 其他用户粉丝列表接口
                                                "/comment/post/{id}", // 获取一级评论
                                                "/comment/post/{id}/{commentId}", // 获取二级评论
                                                "/post/user/{id}", // 获取用户帖子列表
//                                                "/upload/avatar", // 上传头像
//                                                "/upload/post", // 上传帖子图片
                                                "/user/list/post", // 获取用户帖子列表
                                                "/category", // 获取帖子分类
                                                "/ws/**"
                                        ).permitAll() // 放行
                                        .requestMatchers(HttpMethod.GET, "/post/{id}", "/post/list",
                                                "/post/follow/list", "/post/user/{id}", "/comment/{id}",
                                                "/user/list/post", "/user/list/user").permitAll()
                                        .requestMatchers(HttpMethod.DELETE, "/post/{id}").authenticated()
                                        .requestMatchers(
                                                "/admin/**"
                                        ).hasRole("ADMIN")
                                        .requestMatchers(
                                                "/reviewer/**"
                                        ).hasRole("REVIEWER")
                                        .anyRequest() // 所有请求
                                        .authenticated() // 需要认证
                )
//                .formLogin(
//                        login -> login
//                                .loginProcessingUrl("/user/login").permitAll() // 前端登录接口
//                                .successHandler(new MyAuthenticationSuccessHandler()) // 登录成功处理
//                                .failureHandler(new MyAuthenticationFailHandler()) // 登录失败处理
//                )// 自定义登录页面 .permitAll() 表示登录页面可以任意访问
//        ;
//        // 注销
//        http.logout(logout -> logout
//                .logoutSuccessHandler(new MyLogoutHandler()) // 注销成功处理
//                .logoutUrl("/user/logout") // 注销接口 post 请求
//        );
        ;
        // 错误信息
        http.exceptionHandling(
                exception -> {
                    exception.authenticationEntryPoint(new MyAuthenticationEntryPoint());
                    exception.accessDeniedHandler(new MyAccessDeniedHandler());// 拒绝访问处理 用户没有访问权限的时候
                }
        );
        // 跨域
        http.cors(cors -> {
            CorsConfiguration config = new CorsConfiguration();
            config.addAllowedOrigin("http://127.0.0.1:5173"); // 必须统一为 127.0.0.1或者 localhost
            config.addAllowedMethod("*");
            config.addAllowedHeader("*");
            config.setAllowCredentials(true);

            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", config);
            cors.configurationSource(source);
        });
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class); // 添加 JWT 认证过滤器
        // 关闭csrf攻击防御
        http.csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

}
