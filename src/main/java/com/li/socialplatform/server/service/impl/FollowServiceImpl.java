package com.li.socialplatform.server.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.common.constant.MessageConstant;
import com.li.socialplatform.common.utils.AsyncTaskUtil;
import com.li.socialplatform.common.utils.DataCacheUtil;
import com.li.socialplatform.common.utils.RedisUtil;
import com.li.socialplatform.common.utils.UserIdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.socialplatform.server.mapper.FollowMapper;
import com.li.socialplatform.server.mapper.PostMapper;
import com.li.socialplatform.server.mapper.UserMapper;
import com.li.socialplatform.pojo.entity.Follow;
import com.li.socialplatform.pojo.entity.Post;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.pojo.entity.User;
import com.li.socialplatform.pojo.vo.UserVO;
import com.li.socialplatform.server.service.IFollowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.*;

/**
 * @author e69d8e
 * @since 2025/12/8 23:02
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    private final RedisUtil redisUtil;
    private final UserMapper userMapper;
    private final PostMapper postMapper;
    private final UserIdUtil userIdUtil;
    private final AsyncTaskUtil asyncTaskUtil;
    private final DataCacheUtil dataCacheUtil;

    @Override
    public Result follow(Long id) {
        if (id == null) {
            return Result.error(MessageConstant.ID_IS_NULL);
        }
        Long userId = userIdUtil.getUserId();
        if (Objects.equals(userId, id)) {
            return Result.error(MessageConstant.USER_CANNOT_FOLLOW_SELF);
        }
        if (userMapper.selectById(id) == null) {
            return Result.error(MessageConstant.USER_NOT_EXIST);
        }
        // Redis 去重：检查是否已关注（缓存未命中时自动从 DB 加载）
        if (dataCacheUtil.isFollowed(userId, id)) {
            return Result.error(MessageConstant.USER_IS_FOLLOWED);
        }
        long time = System.currentTimeMillis();
        // 粉丝数加一
        Long increment = redisUtil.increment(KeyConstant.FOLLOW_COUNT_KEY + id, 1);
        // 缓存粉丝列表
        redisUtil.zsets().add(KeyConstant.FANS_LIST_KEY + id, userId, time);
        // 缓存关注列表
        redisUtil.zsets().add(KeyConstant.Follow_LIST_KEY + userId, id, time);
        // 续期 TTL
        dataCacheUtil.setFollowTTL(userId, id);
        // 同步落库，靠 follow 表唯一键 uk_follower_followee 兜底并发重复关注
        try {
            baseMapper.insert(new Follow(null, userId, id, null));
        } catch (DuplicateKeyException e) {
            // 并发下另一请求已关注成功：回滚 Redis 侧的计数与列表
            redisUtil.increment(KeyConstant.FOLLOW_COUNT_KEY + id, -1);
            redisUtil.zsets().remove(KeyConstant.FANS_LIST_KEY + id, userId);
            redisUtil.zsets().remove(KeyConstant.Follow_LIST_KEY + userId, id);
            return Result.error(MessageConstant.USER_IS_FOLLOWED);
        }
        // 异步同步 ES
        if (increment != null) {
            asyncTaskUtil.syncUserFansCount(id, increment.intValue());
        }
        // 将要关注用户的帖子查找出来
        Set<ZSetOperations.TypedTuple<Object>> typedTuples =
                redisUtil.zsets().rangeWithScores(KeyConstant.POST_KEY + id, 0, -1);
        String key = KeyConstant.POST_LIST_KEY + userId;
        if (typedTuples == null || typedTuples.isEmpty()) {
            List<Post> posts = postMapper.selectList(new LambdaQueryWrapper<Post>()
                    .eq(Post::getUserId, id)
                    .eq(Post::getEnabled, true)
                    .orderByDesc(Post::getCreateTime)
                    .last("LIMIT 20"));
            if (!posts.isEmpty()) {
                String authorPostKey = KeyConstant.POST_KEY + id;
                Map<Object, Double> memberScores = new HashMap<>(posts.size());
                for (Post p : posts) {
                    double score = p.getCreateTime() != null
                            ? p.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            : (double) System.currentTimeMillis();
                    memberScores.put(p.getId(), score);
                }
                // pipeline 批量回填作者帖子缓存与关注流缓存
                redisUtil.pipeline(ops -> {
                    for (Map.Entry<Object, Double> entry : memberScores.entrySet()) {
                        ops.opsForZSet().add(authorPostKey, entry.getKey(), entry.getValue());
                        ops.opsForZSet().add(key, entry.getKey(), entry.getValue());
                    }
                });
            }
            return Result.ok(MessageConstant.FOLLOW_SUCCESS, "");
        }
        Map<Object, Double> memberScores = new HashMap<>(typedTuples.size());
        for (ZSetOperations.TypedTuple<Object> tuple : typedTuples) {
            memberScores.put(tuple.getValue(), tuple.getScore());
        }
        redisUtil.zAddAll(key, memberScores);
        return Result.ok(MessageConstant.FOLLOW_SUCCESS, "");
    }

    @Override
    public Result cancelFollow(Long id) {
        if (id == null) {
            return Result.error(MessageConstant.ID_IS_NULL);
        }
        Long userId = userIdUtil.getUserId();
        if (Objects.equals(userId, id)) {
            return Result.error(MessageConstant.USER_CANNOT_FOLLOW_SELF);
        }
        // Redis 去重：检查是否已关注（缓存未命中时自动从 DB 加载）
        if (!dataCacheUtil.isFollowed(userId, id)) {
            return Result.error(MessageConstant.USER_NOT_FOLLOWED, List.of());
        }
        // 粉丝数减一
        Long increment = redisUtil.increment(KeyConstant.FOLLOW_COUNT_KEY + id, -1);
        redisUtil.zsets().remove(KeyConstant.FANS_LIST_KEY + id, userId);
        redisUtil.zsets().remove(KeyConstant.Follow_LIST_KEY + userId, id);
        // 续期 TTL
        dataCacheUtil.setFollowTTL(userId, id);
        // 异步删除 MySQL
        asyncTaskUtil.asyncDeleteFollowRecord(userId, id);
        // 异步同步 ES
        if (increment != null) {
            asyncTaskUtil.syncUserFansCount(id, increment.intValue());
        }
        // 将要取关的用户的帖子查找出来
        Set<ZSetOperations.TypedTuple<Object>> typedTuples =
                redisUtil.zsets().rangeWithScores(KeyConstant.POST_KEY + id, 0, -1);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok(MessageConstant.UN_FOLLOW_SUCCESS, "");
        }
        List<Object> ids = typedTuples.stream()
                .map(ZSetOperations.TypedTuple::getValue).toList();
        String key = KeyConstant.POST_LIST_KEY + userId;
        redisUtil.pipeline(ops -> {
            for (Object o : ids) {
                ops.opsForZSet().remove(key, o);
            }
        });
        return Result.ok(MessageConstant.UN_FOLLOW_SUCCESS, "");
    }

    @Override
    public Result getFollowerList(Long id, Integer pageNum, Integer pageSize) {
        // 如果id为空 说明查询自己的粉丝列表
        if (id == null) {
            id = userIdUtil.getUserId();
        } else {
            // 查询该用户粉丝列表是否为私密
            User u = userMapper.selectById(id);
            if (u.getFansPrivate()) {
                return Result.error(MessageConstant.USER_FANS_PRIVATE, List.of());
            }
        }
        long start = (long) (pageNum - 1) * pageSize;
        long end = start + pageSize - 1;
        // 缓存未命中时回源 DB（列表 ZSet 过期后不再静默返回空）
        dataCacheUtil.ensureFollowDataLoaded(id);
        Long total = redisUtil.zsets().size(KeyConstant.FANS_LIST_KEY + id);
        if (total == null) {
            return Result.ok(List.of(), 0L);
        }
        if (start > total) {
            return Result.ok(List.of(), 0L);
        }
        if (end > total) {
            end = total - 1;
        }
        // 获取粉丝列表
        Set<Object> follower = redisUtil.zsets().range(KeyConstant.FANS_LIST_KEY + id, start, end);
        if (follower == null || follower.isEmpty()) {
            return Result.ok(List.of(), 0L);
        }
        List<Long> ids = follower.stream().map(member -> Long.valueOf(member.toString())).toList();
        // 获取当前用户
        Long userId = userIdUtil.getUserId();
        List<User> users = userMapper.selectByIds(ids);
        // 批量读取粉丝数与关注状态（pipeline 一次往返）
        Map<Long, Integer> fansCounts = dataCacheUtil.getFollowerCounts(ids);
        Map<Long, Boolean> followedMap = userId != null
                ? dataCacheUtil.isFollowedBatch(userId, ids) : Map.of();
        List<UserVO> userVOs = users.stream().map(user -> {
            UserVO userVO = BeanUtil.copyProperties(user, UserVO.class);
            userVO.setFansCount(fansCounts.getOrDefault(user.getId(), 0));
            userVO.setFollowed(followedMap.getOrDefault(user.getId(), false));
            return userVO;
        }).toList();
        return Result.ok(userVOs, total);
    }


    @Override
    public Result getFolloweeList(Long id, Integer pageNum, Integer pageSize) {
        boolean isSelf = id == null;
        if (isSelf) {
            id = userIdUtil.getUserId();
        } else {
            // 查询该用户关注列表是否为私密
            User u = userMapper.selectById(id);
            if (u.getFollowPrivate()) {
                return Result.error(MessageConstant.USER_FOLLOW_PRIVATE, List.of());
            }
        }
        long start = (long) (pageNum - 1) * pageSize;
        long end = start + pageSize - 1;
        // 缓存未命中时回源 DB
        dataCacheUtil.ensureFollowDataLoaded(id);
        Long total = redisUtil.zsets().size(KeyConstant.Follow_LIST_KEY + id);
        if (total == null) {
            return Result.ok(List.of(), 0L);
        }
        if (start > total) {
            return Result.ok(List.of(), 0L);
        }
        if (end > total) {
            end = total - 1;
        }
        // 查询该用户关注列表
        Set<Object> followee = redisUtil.zsets().range(KeyConstant.Follow_LIST_KEY + id, start, end);
        if (followee == null || followee.isEmpty()) {
            return Result.ok(List.of(), 0L);
        }
        List<Long> ids = followee.stream().map(member -> Long.valueOf(member.toString())).toList();
        List<User> users = userMapper.selectByIds(ids);
        Long userId = userIdUtil.getUserId();
        // 批量读取粉丝数与关注状态（pipeline 一次往返）
        Map<Long, Integer> fansCounts = dataCacheUtil.getFollowerCounts(ids);
        Map<Long, Boolean> followedMap = !isSelf && userId != null
                ? dataCacheUtil.isFollowedBatch(userId, ids) : Map.of();
        List<UserVO> userVOs = new ArrayList<>();
        for (User user : users) {
            UserVO userVO = BeanUtil.copyProperties(user, UserVO.class);
            if (isSelf) {
                userVO.setFollowed(true);
            } else {
                userVO.setFollowed(followedMap.getOrDefault(user.getId(), false));
            }
            userVO.setFansCount(fansCounts.getOrDefault(user.getId(), 0));
            userVOs.add(userVO);
        }
        return Result.ok(userVOs, total);
    }

    @Override
    public Result getFriendList(Integer pageNum, Integer pageSize) {
        // 查询我关注的人和关注我的人的交集
        // 获取当前用户id
        Long userId = userIdUtil.getUserId();
        long start = (long) (pageNum - 1) * pageSize;
        long end = start + pageSize - 1;
        // 缓存未命中时回源 DB
        dataCacheUtil.ensureFollowDataLoaded(userId);
        Long total = redisUtil.zsets().intersectAndStore(
                KeyConstant.FANS_LIST_KEY + userId,
                KeyConstant.Follow_LIST_KEY + userId,
                KeyConstant.FRIEND_LIST_KEY + userId);
        Set<Object> friend = redisUtil.zsets().range(KeyConstant.FRIEND_LIST_KEY + userId, start, end);
        if (friend == null || friend.isEmpty()) {
            return Result.ok(List.of(), 0L);
        }
        List<Long> ids = friend.stream().map(member -> Long.valueOf(member.toString())).toList();
        List<User> users = userMapper.selectByIds(ids);
        // 批量读取粉丝数（pipeline 一次往返）
        Map<Long, Integer> fansCounts = dataCacheUtil.getFollowerCounts(ids);
        List<UserVO> userVOs = new ArrayList<>();
        for (User user : users) {
            UserVO userVO = BeanUtil.copyProperties(user, UserVO.class);
            userVO.setFollowed(true);
            userVO.setFansCount(fansCounts.getOrDefault(user.getId(), 0));
            userVOs.add(userVO);
        }
        return Result.ok(userVOs, total);
    }
}
