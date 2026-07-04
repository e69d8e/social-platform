package com.li.socialplatform.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.socialplatform.server.mapper.AuthorityMapper;
import com.li.socialplatform.server.mapper.UserMapper;
import com.li.socialplatform.pojo.entity.Authority;
import com.li.socialplatform.pojo.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/**
 * @author e69d8e
 * @since 2025/12/8 15:04
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DBUserDetailsManager implements UserDetailsService {

    private final UserMapper userMapper;
    private final AuthorityMapper authorityMapper;
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, username);
        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {
            throw new UsernameNotFoundException("用户 " + username + " 不存在");
        } else {
            // 将数据库权限表的数据添加到权限列表中
            Authority authority = authorityMapper.selectById(user.getAuthorityId());
            if (authority == null) {
                throw new UsernameNotFoundException("用户 " + username + " 的权限信息不存在");
            }
            log.info("用户角色：{}", authority);
            // 构建 UserDetail 对象
            return org.springframework.security.core.userdetails.User.withUsername(user.getUsername()) //自定义用户名
                    .password(user.getPassword()) //自定义密码
                    .disabled(!user.getEnabled()) // 用户账号是否禁用
                    .accountExpired(!user.getEnabled()) // 用户凭证是否过期
                    .accountLocked(!user.getEnabled()) // 用户是否被锁定
                    .roles(authority.getAuthority()) // 添加角色
                    .build();
        }
    }
}
