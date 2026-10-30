package com.li.socialplatform.server.service.impl;

import cn.hutool.core.bean.BeanUtil;
import co.elastic.clients.elasticsearch._types.FieldValue;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.common.constant.MessageConstant;
import com.li.socialplatform.common.properties.SystemConstants;
import com.li.socialplatform.common.utils.DataCacheUtil;
import com.li.socialplatform.common.utils.DeleteFileUtil;
import com.li.socialplatform.common.utils.JwtUtils;
import com.li.socialplatform.common.utils.RedisUtil;
import com.li.socialplatform.common.utils.TokenCacheUtil;
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
import com.li.socialplatform.server.service.ICaptchaService;
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
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
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
import java.util.*;
import java.util.concurrent.TimeUnit;

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
    private final RedisUtil redisUtil;
    private final TokenCacheUtil tokenCacheUtil;
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
    private final ICaptchaService captchaService;

    @Value("${jwt.access-expire}")
    private Long accessExpire;

    @Value("${jwt.refresh-expire}")
    private Long refreshExpire;

    // 密码加密
    private String encodePassword(String password) {
        return passwordEncoder.encode(password);
    }

    // 获取当前登录用户的用户名
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }

    @Override
    public Result login(LoginDTO loginDTO) {
        // 校验滑块验证码（原子消费 verifyToken，防止重放）
        if (!captchaService.consumeVerifyToken(loginDTO.getVerifyToken())) {
            return Result.error(MessageConstant.CAPTCHA_NOT_VERIFIED);
        }
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
        tokenCacheUtil.setRefreshToken(username, refreshToken, refreshExpire, TimeUnit.MILLISECONDS);

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
        String storedRefreshToken = tokenCacheUtil.getRefreshToken(username);
        if (!refreshToken.equals(storedRefreshToken)) {
            return Result.error("refresh token 不匹配或已失效");
        }
        // 3. 将旧 access token 加入黑名单
        blacklistAccessToken(request);
        // 4. 生成新的 token
        String newAccessToken = jwtUtils.generateToken(username, accessExpire);
        String newRefreshToken = jwtUtils.generateToken(username, refreshExpire);
        tokenCacheUtil.setRefreshToken(username, newRefreshToken, refreshExpire, TimeUnit.MILLISECONDS);
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
                    tokenCacheUtil.blacklistToken(jti, remaining);
                }
            } catch (Exception e) {
                log.warn("将 token 加入黑名单失败: {}", e.getMessage());
            }
        }
    }

    @Override
    public Result logout(HttpServletRequest request) {
        String username = getCurrentUsername();
        tokenCacheUtil.deleteRefreshToken(username);
        blacklistAccessToken(request);
        return Result.ok(MessageConstant.USER_LOGOUT_SUCCESS, "");
    }

    @Override
    public Result register(UserDTO userDTO) {
        if (userDTO == null || userDTO.getUsername() == null || userDTO.getPassword() == null) {
            return Result.error(MessageConstant.USER_IS_EMPTY);
        }
        // 校验滑块验证码（原子消费 verifyToken，防止重放）
        if (!captchaService.consumeVerifyToken(userDTO.getVerifyToken())) {
            return Result.error(MessageConstant.CAPTCHA_NOT_VERIFIED);
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
        // 背景图：null 表示未修改，空串表示恢复默认背景
        user.setBackgroundUrl(userDTO.getBackgroundUrl() == null ? user.getBackgroundUrl() : userDTO.getBackgroundUrl());
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
        // 删除旧背景图
        if (userDTO.getBackgroundUrl() != null
                && userDTO.getOldBackgroundUrl() != null
                && !userDTO.getOldBackgroundUrl().isEmpty()
                && !userDTO.getOldBackgroundUrl().equals(userDTO.getBackgroundUrl())) {
            String url = userDTO.getOldBackgroundUrl().substring(systemConstants.baseUrl.length());
            int delete = fileMapper.delete(new LambdaQueryWrapper<File>().eq(File::getUrl, url).eq(File::getUserId, user.getId()));
            if (delete > 0) {
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
        // 设置签到
        redisUtil.values().setBit(key, now.getDayOfMonth() - 1, true);
        return Result.ok(calculateConsecutiveDays(now));
    }

    @Override
    public Result signInCount() {
        return Result.ok(calculateConsecutiveDays(LocalDateTime.now()));
    }

    /** 连续签到回溯上限（天），防御异常数据导致死循环 */
    private static final int MAX_SIGN_IN_STREAK_DAYS = 3660;

    /**
     * 计算截至今天的连续签到天数（跨月累计）：
     * 今天已签到则从今天起算；今天尚未签到则从昨天起算（当天补签不打断连续性）。
     * 逐日回溯查询 Redis 位图，跨月时自动切换到上月的签到 key。
     */
    private int calculateConsecutiveDays(LocalDateTime now) {
        if (!isSignedOn(now)) {
            now = now.minusDays(1);
            if (!isSignedOn(now)) {
                return 0;
            }
        }
        int days = 0;
        while (isSignedOn(now) && days < MAX_SIGN_IN_STREAK_DAYS) {
            days++;
            now = now.minusDays(1);
        }
        return days;
    }

    /** 查询某天是否签到（按月位图存储） */
    private boolean isSignedOn(LocalDateTime dateTime) {
        String key = KeyConstant.SIGN_IN_KEY + getCurrentUsername()
                + dateTime.format(DateTimeFormatter.ofPattern("yyyyMM"));
        return Boolean.TRUE.equals(redisUtil.values().getBit(key, dateTime.getDayOfMonth() - 1));
    }

    @Override
    public Result listPost(String keyword, Integer pageNum, Integer pageSize, Integer categoryId) {
        Sort sort = Sort.by(Sort.Direction.DESC, "likeCount");
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, sort);
        List<Post> posts;
        Long total;
        // 所有搜索入口都只返回未封禁（enabled=true）的帖子，与推荐流 getScrollResult 行为一致
        if (StringUtils.isEmpty(keyword)) {
            if (categoryId == null) {
                posts = postElasticsearchRepository.findByEnabledTrue(pageable);
                total = postElasticsearchRepository.countByEnabledTrue();
            } else {
                posts = postElasticsearchRepository.findByCategoryIdAndEnabledTrue(categoryId, pageable);
                total = postElasticsearchRepository.countByCategoryIdAndEnabledTrue(categoryId);
            }
        } else {
            posts = postElasticsearchRepository.searchByKeywordAndEnabled(keyword, pageable);
            // count 与仓库 @Query 搜索同条件；不用派生 count，Or 混合 And 时方法名拆分语义易错
            NativeQuery countQuery = NativeQuery.builder()
                    .withQuery(q -> q.bool(b -> b
                            .should(s -> s.match(m -> m.field("title").query(keyword)))
                            .should(s -> s.match(m -> m.field("content").query(keyword)))
                            .minimumShouldMatch("1")
                            .filter(f -> f.term(t -> t.field("enabled").value(FieldValue.of(true))))
                            .mustNot(mn -> mn.term(t -> t.field("isPrivate").value(FieldValue.of(true))))))
                    .build();
            total = elasticsearchOperations.count(countQuery, Post.class);
        }
        if (!StringUtils.isEmpty(keyword)) {
            searchHistoryService.recordSearch(keyword, 0);
        }
        if (posts.isEmpty()) {
            return Result.ok(List.of(), 0L);
        }
        List<Long> userIds = posts.stream()
                .map(Post::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            userMapper.selectBatchIds(userIds).forEach(u -> userMap.put(u.getId(), u));
        }

        List<PostVO> postVOS = new ArrayList<>();
        Long userId = userIdUtil.getUserId();
        // 批量读取点赞数与点赞状态（pipeline 一次往返）
        List<Long> postIds = posts.stream().map(Post::getId).toList();
        Map<Long, Integer> likeCounts = dataCacheUtil.getLikeCounts(postIds);
        Map<Long, Boolean> likedMap = dataCacheUtil.isLikedBatch(userId, postIds);
        for (Post post : posts) {
            PostVO postVO = BeanUtil.copyProperties(post, PostVO.class);
            User author = userMap.get(post.getUserId());
            if (author != null) {
                postVO.setUsername(author.getUsername());
                postVO.setNickname(author.getNickname());
            }
            postVO.setLiked(likedMap.getOrDefault(post.getId(), false));
            postVO.setLikeCount(likeCounts.getOrDefault(post.getId(), 0));
            postVOS.add(postVO);
        }
        return Result.ok(postVOS, total);
    }

    @Override
    public Result listUser(String keyword, Integer pageNum, Integer pageSize) {
        Sort sort = Sort.by(Sort.Direction.DESC, "fansCount");
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, sort);
        List<User> users = userElasticsearchRepository.findByUsernameOrNickname(keyword, keyword, pageable);
        // 使用 ElasticsearchOperations 统一 count 查询（与 find 使用相同的 edge_ngram 查询 + enabled 过滤）
        long total = 0;
        if (!users.isEmpty()) {
            NativeQuery countQuery = NativeQuery.builder()
                    .withQuery(q -> q.bool(b -> b
                            .must(m -> m.multiMatch(mm -> mm.fields("username.edge", "nickname.edge").query(keyword)))
                            .filter(f -> f.term(t -> t.field("enabled").value(FieldValue.of(true))))))
                    .build();
            total = elasticsearchOperations.count(countQuery, User.class);
        }
        if (!StringUtils.isEmpty(keyword)) {
            searchHistoryService.recordSearch(keyword, 1);
        }
        if (users.isEmpty()) {
            return Result.ok(List.of(), 0L);
        }
        Long currentUserId = userIdUtil.getUserId();
        List<Long> resultUserIds = users.stream().map(User::getId).toList();
        // 批量读取粉丝数与关注状态（pipeline 一次往返）
        Map<Long, Integer> fansCounts = dataCacheUtil.getFollowerCounts(resultUserIds);
        Map<Long, Boolean> followedMap = dataCacheUtil.isFollowedBatch(currentUserId, resultUserIds);
        List<UserVO> userVOS = new ArrayList<>();
        for (User user : users) {
            UserVO userVO = BeanUtil.copyProperties(user, UserVO.class);
            userVO.setFollowed(followedMap.getOrDefault(user.getId(), false));
            userVO.setFansCount(fansCounts.getOrDefault(user.getId(), 0));
            userVOS.add(userVO);
        }
        return Result.ok(userVOS, total);
    }
}
