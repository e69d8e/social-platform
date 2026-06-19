package com.li.socialplatform.server.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.common.constant.MessageConstant;
import com.li.socialplatform.common.properties.SystemConstants;
import com.li.socialplatform.common.utils.DataCacheUtil;
import com.li.socialplatform.common.utils.DeleteFileUtil;
import com.li.socialplatform.common.utils.JwtUtils;
import com.li.socialplatform.common.utils.UserIdUtil;
import com.li.socialplatform.pojo.dto.LoginDTO;
import com.li.socialplatform.pojo.dto.RefreshDTO;
import com.li.socialplatform.pojo.entity.File;
import com.li.socialplatform.pojo.vo.TokenVO;
import com.li.socialplatform.server.mapper.FileMapper;
import com.li.socialplatform.server.repository.PostElasticsearchRepository;
import com.li.socialplatform.server.repository.UserElasticsearchRepository;
import com.li.socialplatform.server.mapper.UserMapper;
import com.li.socialplatform.pojo.dto.UserDTO;
import com.li.socialplatform.pojo.entity.Post;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.pojo.entity.User;
import com.li.socialplatform.pojo.vo.PostVO;
import com.li.socialplatform.pojo.vo.UserVO;
import com.li.socialplatform.server.service.ISearchHistoryService;
import com.li.socialplatform.server.service.IUserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.micrometer.common.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.li.socialplatform.common.constant.KeyConstant.TOKEN_BLACKLIST_KEY;

/**
 * @author e69d8e
 * @since 2025/12/8 15:45
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private final UserMapper userMapper;
    private final SystemConstants systemConstants;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserIdUtil userIdUtil;
    private final UserElasticsearchRepository userElasticsearchRepository;
    private final PostElasticsearchRepository postElasticsearchRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final JwtUtils jwtUtils;
    private final AuthenticationManager authenticationManager;
    private final DeleteFileUtil deleteFileUtil;
    private final FileMapper fileMapper;
    private final DataCacheUtil dataCacheUtil;
    private final ISearchHistoryService searchHistoryService;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.access-expire}")
    private Long accessExpire;

    @Value("${jwt.refresh-expire}")
    private Long refreshExpire;

    // 密码加密
    private String encodePassword(String password) {
        return "{bcrypt}" + passwordEncoder.encode(password);
    }

    // 获取当前登录用户的用户名
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }

    @Override
    public Result login(LoginDTO loginDTO) {
        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(loginDTO.getUsername(), loginDTO.getPassword());

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(authToken);
        } catch (DisabledException e) {
            return Result.error(MessageConstant.USER_NOT_ENABLED);
        } catch (AuthenticationException e) {
            return Result.error(MessageConstant.USER_PASSWORD_ERROR);
        }

        UserDetails authenticatedUser = (UserDetails) authentication.getPrincipal();
        String username = authenticatedUser.getUsername();

        String accessToken = jwtUtils.generateToken(username, accessExpire);
        String refreshToken = jwtUtils.generateToken(username, refreshExpire);
        redisTemplate.opsForValue().set(KeyConstant.REFRESH_KEY + username, refreshToken, refreshExpire, TimeUnit.MILLISECONDS);

        return Result.ok(MessageConstant.USER_LOGIN_SUCCESS, new TokenVO(accessToken, refreshToken));
    }

    @Override
    public Result refresh(RefreshDTO refreshDTO, HttpServletRequest request) {
        String refreshToken = refreshDTO.getRefreshToken();
        if (StringUtils.isEmpty(refreshToken)) {
            return Result.error("refresh token 不能为空");
        }
        // 1. 解析 refreshToken 获取用户名
        Claims claims;
        try {
            claims = jwtUtils.parseToken(refreshToken);
        } catch (ExpiredJwtException e) {
            return Result.error("refresh token 已过期，请重新登录");
        } catch (JwtException e) {
            return Result.error("无效的 refresh token");
        }
        String username = claims.getSubject();
        // 2. 从 Redis 中获取保存的 refreshToken 并比对
        String storedRefreshToken = (String) redisTemplate.opsForValue().get(KeyConstant.REFRESH_KEY + username);
        if (!refreshToken.equals(storedRefreshToken)) {
            return Result.error("refresh token 不匹配或已失效");
        }
        // 3. 将旧 access token 加入黑名单
        blacklistAccessToken(request);
        // 4. 生成新的 token
        String newAccessToken = jwtUtils.generateToken(username, accessExpire);
        String newRefreshToken = jwtUtils.generateToken(username, refreshExpire);
        redisTemplate.opsForValue().set(KeyConstant.REFRESH_KEY + username, newRefreshToken, refreshExpire, TimeUnit.MILLISECONDS);
        return Result.ok(new TokenVO(newAccessToken, newRefreshToken));
    }

    private void blacklistAccessToken(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            try {
                String jti = jwtUtils.getTokenId(token);
                long remaining = jwtUtils.getRemainingExpiration(token);
                if (remaining > 0) {
                    redisTemplate.opsForValue().set(TOKEN_BLACKLIST_KEY + jti, "", remaining, TimeUnit.MILLISECONDS);
                }
            } catch (Exception e) {
                log.warn("将 token 加入黑名单失败: {}", e.getMessage());
            }
        }
    }

    @Override
    public Result logout(HttpServletRequest request) {
        String username = getCurrentUsername();
        redisTemplate.delete(KeyConstant.REFRESH_KEY + username);
        blacklistAccessToken(request);
        return Result.ok(MessageConstant.USER_LOGOUT_SUCCESS, "");
    }

    @Override
    public Result register(UserDTO userDTO) {
        if (userDTO == null || userDTO.getUsername() == null || userDTO.getPassword() == null) {
            return Result.error(MessageConstant.USER_IS_EMPTY);
        }
        // 使用正则校验
        if (!userDTO.getUsername().matches("^[a-zA-Z0-9_-]{4,16}$")) {
            return Result.error(MessageConstant.USERNAME_FORMAT_ERROR);
        }
        if (!userDTO.getPassword().matches("^[a-zA-Z0-9_-]{6,16}$")) {
            return Result.error(MessageConstant.PASSWORD_FORMAT_ERROR);
        }
        // 判断是否已经有该用户名
        User existingUser = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, userDTO.getUsername()));
        if (existingUser != null) {
            return Result.error(MessageConstant.USERNAME_ALREADY_EXISTS);
        }
        User user = new User();
        // 使用UUID生成随机昵称
        String uuid = UUID.randomUUID().toString().replace("-", "");
        user.setNickname(systemConstants.userNicknamePrefix + uuid.substring(10));
        user.setPassword(encodePassword(userDTO.getPassword()));
        user.setUsername(userDTO.getUsername());
        user.setAvatar(systemConstants.defaultAvatar);
        userMapper.insert(user);
        // 将用户信息存入 Elasticsearch
        user.setFansCount(0);
        userElasticsearchRepository.save(user);
        return Result.ok(MessageConstant.REGISTER_SUCCESS, "");
    }


    @Override
    public Result getUserProfile(Long id) {
        User user;
        boolean followed = false;
        if (id == null) {
            // 查询登录用户的信息
            user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, getCurrentUsername()));
            if (user == null) {
                // 用户未登录
                throw new AccessDeniedException(MessageConstant.USER_NOT_LOGIN);
            }
            id = user.getId();
        } else {
            // 查询其他用户的信息
            user = userMapper.selectById(id);
            if (user == null) {
                return Result.error(MessageConstant.USER_NOT_FOUND);
            }
            // 查询当前用户有没有关注
            Long userId = userIdUtil.getUserId();
            if (userId != null) {
                followed = dataCacheUtil.isFollowed(userId, id);
            }
        }
        // 获取角色
        UserVO userVO = BeanUtil.copyProperties(user, UserVO.class);
        userVO.setFansCount(dataCacheUtil.getFollowerCount(id));
        userVO.setFollowed(followed);
        return Result.ok(userVO);
    }

    @Override
    public Result updateUserProfile(UserDTO userDTO) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, getCurrentUsername()));
        if (userDTO.getGender() == null || userDTO.getGender() < 0 || userDTO.getGender() > 2) {
            return Result.error(MessageConstant.USER_INFO_ERROR);
        }
        if (userDTO.getNickname() == null || userDTO.getNickname().isEmpty() || userDTO.getNickname().length() > Integer.parseInt(systemConstants.nicknameMaxLength)) {
            return Result.error(MessageConstant.NICKNAME_ERROR);
        }
        if (userDTO.getAvatar() == null || userDTO.getAvatar().isEmpty()) {
            return Result.error(MessageConstant.USER_INFO_ERROR);
        }
        if (userDTO.getBio() != null && userDTO.getBio().length() > Integer.parseInt(systemConstants.bioMaxLength)) {
            return Result.error(MessageConstant.BIO_ERROR);
        }
        if (user == null) {
            return Result.error(MessageConstant.USER_NOT_FOUND);
        }
        // 更新用户信息
        user.setNickname(userDTO.getNickname());
        user.setAvatar(userDTO.getAvatar() == null || userDTO.getAvatar().isEmpty() ? user.getAvatar() : userDTO.getAvatar());
        user.setBio(userDTO.getBio() == null ? user.getBio() : userDTO.getBio());
        user.setGender(userDTO.getGender() == null ? user.getGender() : userDTO.getGender());
        user.setFansPrivate(userDTO.getFansPrivate() == null ? user.getFansPrivate() : userDTO.getFansPrivate());
        user.setFollowPrivate(userDTO.getFollowPrivate() == null ? user.getFollowPrivate() : userDTO.getFollowPrivate());
        userMapper.updateById(user);
        // 更新 Elasticsearch
        userElasticsearchRepository.save(user);
        // 删除旧头像
        if (userDTO.getAvatar() != null
                && !userDTO.getAvatar().isEmpty()
                && !userDTO.getAvatar().equals(userDTO.getOldAvatar())
                && userDTO.getOldAvatar() != null
                && !userDTO.getOldAvatar().equals(systemConstants.defaultAvatar)) {
            String url = userDTO.getOldAvatar().substring(systemConstants.baseUrl.length());
            int delete = fileMapper.delete(new LambdaQueryWrapper<File>().eq(File::getUrl, url).eq(File::getUserId, user.getId()));
            if (delete >0) {
                deleteFileUtil.deleteFile(url);
            }
        }
        return Result.ok(MessageConstant.UPDATE_SUCCESS, "");
    }

    @Override
    public Result updatePassword(UserDTO userDTO) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, getCurrentUsername()));
        if (user == null) {
            return Result.error(MessageConstant.USER_NOT_FOUND);
        }
        user.setPassword(encodePassword(userDTO.getPassword()));
        userMapper.updateById(user);
        return Result.ok(MessageConstant.UPDATE_SUCCESS, "");
    }

    @Override
    public Result signIn() {
        LocalDateTime now = LocalDateTime.now();
        String key = KeyConstant.SIGN_IN_KEY + getCurrentUsername() + now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        // 获取今天是当月的第几天
        int day = now.getDayOfMonth();
        // 设置签到
        redisTemplate.opsForValue().setBit(key, day - 1, true);
        // 计算当月截至今天连续签到次数
        List<Long> longs = redisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(day)).valueAt(0) // 从第0位开始读取
        );
        // 0000000011
        Long tmp = null;
        if (longs != null) {
            tmp = longs.getFirst();
        }
        int count = 0;
        while (tmp != null && tmp != 0) {
            if ((tmp & 1) == 1) {
                count++;
            } else {
                break;
            }
            tmp = tmp >> 1;
        }
        return Result.ok(count);
    }

    @Override
    public Result signInCount() {
        LocalDateTime now = LocalDateTime.now();
        String key = KeyConstant.SIGN_IN_KEY + getCurrentUsername() + now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        List<Long> longs = redisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(now.getDayOfMonth())).valueAt(0) // 从第0位开始读取
        );
        Long tmp = null;
        if (longs != null) {
            tmp = longs.getFirst();
        }
        int count = 0;
        while (tmp != null && tmp != 0) {
            if ((tmp & 1) == 1) {
                count++;
            } else {
                break;
            }
            tmp = tmp >>> 1;
        }
        return Result.ok(count);
    }

    @Override
    public Result listPost(String keyword, Integer pageNum, Integer pageSize, Integer categoryId) {
        Sort sort = Sort.by(Sort.Direction.DESC, "likeCount");
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, sort);
        List<Post> posts;
        Long total;
        if (StringUtils.isEmpty(keyword)) {
            if (categoryId == null) {
                posts = postElasticsearchRepository.findAll(pageable).getContent();
                total = postElasticsearchRepository.count();
            } else {
                posts = postElasticsearchRepository.findByCategoryId(categoryId, pageable);
                total = postElasticsearchRepository.countByCategoryId(categoryId);
            }
        } else {
            if (categoryId == null) {
                posts = postElasticsearchRepository.findByTitleOrContent(keyword, keyword, pageable);
                total = postElasticsearchRepository.countByTitleOrContent(keyword, keyword);
            } else {
                posts = postElasticsearchRepository.findByTitleOrContentAndCategoryId(keyword, keyword, categoryId, pageable);
                total = postElasticsearchRepository.countByTitleOrContentAndCategoryId(keyword, keyword, categoryId);
            }
        }
        if (!StringUtils.isEmpty(keyword)) {
            searchHistoryService.recordSearch(keyword, 0);
        }
        if (posts.isEmpty()) {
            return Result.ok(List.of(), 0L);
        }
        List<PostVO> postVOS = new ArrayList<>();
        Long userId = userIdUtil.getUserId();
        for (Post post : posts) {
            PostVO postVO = BeanUtil.copyProperties(post, PostVO.class);
            if (userId == null) {
                postVO.setLiked(false);
            } else {
                postVO.setLiked(dataCacheUtil.isLiked(post.getId(), userId));
            }
            postVO.setLikeCount(dataCacheUtil.getLikeCount(post.getId()));
            postVOS.add(postVO);
        }
        return Result.ok(postVOS, total);
    }

    @Override
    public Result listUser(String keyword, Integer pageNum, Integer pageSize) {
        Sort sort = Sort.by(Sort.Direction.DESC, "fansCount");
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, sort);
        List<User> users = userElasticsearchRepository.findByUsernameOrNickname(keyword, keyword, pageable);
        // 使用 ElasticsearchOperations 统一 count 查询（与 find 使用相同的 edge_ngram 查询）
        long total = 0;
        if (!users.isEmpty()) {
            org.springframework.data.elasticsearch.client.elc.NativeQuery countQuery = org.springframework.data.elasticsearch.client.elc.NativeQuery.builder()
                    .withQuery(q -> q.multiMatch(mm -> mm.fields("username.edge", "nickname.edge").query(keyword)))
                    .build();
            total = elasticsearchOperations.count(countQuery, User.class);
        }
        if (!StringUtils.isEmpty(keyword)) {
            searchHistoryService.recordSearch(keyword, 1);
        }
        if (users.isEmpty()) {
            return Result.ok(List.of(), 0L);
        }
        List<UserVO> userVOS = new ArrayList<>();
        Long currentUserId = userIdUtil.getUserId();
        for (User user : users) {
            UserVO userVO = BeanUtil.copyProperties(user, UserVO.class);
            userVO.setFollowed(currentUserId != null && dataCacheUtil.isFollowed(currentUserId, user.getId()));
            userVO.setFansCount(dataCacheUtil.getFollowerCount(user.getId()));
            userVOS.add(userVO);
        }
        return Result.ok(userVOS, total);
    }
}
