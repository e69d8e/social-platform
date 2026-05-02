package com.li.socialplatform.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.common.constant.MessageConstant;
import com.li.socialplatform.common.utils.UserIdUtil;
import com.li.socialplatform.mapper.FollowMapper;
import com.li.socialplatform.mapper.UserMapper;
import com.li.socialplatform.pojo.entity.Follow;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.pojo.entity.User;
import com.li.socialplatform.pojo.vo.UserVO;
import com.li.socialplatform.service.IFollowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * @author e69d8e
 * @since 2025/12/8 23:02
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    private final FollowMapper followMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserMapper userMapper;
    private final UserIdUtil userIdUtil;
    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    public Result follow(Long id) {
        if (id == null) {
            return Result.error(MessageConstant.ID_IS_NULL);
        }
        // 获取当前用户id
        Long userId = userIdUtil.getUserId();
        if (Objects.equals(userId, id)) {
            return Result.error(MessageConstant.USER_CANNOT_FOLLOW_SELF);
        }
        // 判断用户是否存在
        if (userMapper.selectById(id) == null) {
            return Result.error(MessageConstant.USER_NOT_EXIST);
        }
        Follow follow = followMapper.selectOne(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFolloweeId, id).eq(Follow::getFollowerId, userId));
        if (follow != null) {
            return Result.error(MessageConstant.USER_IS_FOLLOWED);
        }
        long time = System.currentTimeMillis();
        // 粉丝数加一
        Long increment = redisTemplate.opsForValue().increment(KeyConstant.FOLLOW_COUNT_KEY + id, 1);
        // 缓存粉丝列表
        redisTemplate.opsForZSet().add(KeyConstant.FANS_LIST_KEY + id, userId, time);
        // 缓存关注列表
        redisTemplate.opsForZSet().add(KeyConstant.Follow_LIST_KEY + userId, id, time);
        // 添加关注
        followMapper.insert(new Follow(null, userId, id, null));
        // 更新 ElasticSearch
        User user = elasticsearchOperations.get(id.toString(), User.class);
        if (user != null) {
            if (increment != null) {
                user.setCount(increment.intValue());
                elasticsearchOperations.save(user);
            }
        }
        // 将要关注用户的帖子查找出来
        Set<ZSetOperations.TypedTuple<Object>> typedTuples =
                redisTemplate.opsForZSet().rangeWithScores(KeyConstant.POST_KEY + id, 0, -1);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok(MessageConstant.FOLLOW_SUCCESS, "");
        }
        // 解析
        // 获取 id
        List<Long> ids = typedTuples.stream()
                .map(ZSetOperations.TypedTuple::getValue).map(String::valueOf).map(Long::valueOf).toList();
        // 获取 score
        List<Double> scores = typedTuples.stream()
                .map(ZSetOperations.TypedTuple::getScore).toList();
        // 关注后要将该用户的所有帖子推送到我的关注
        String key = KeyConstant.POST_LIST_KEY + userId;
        for (int i = 0; i < ids.size(); i++) {
            redisTemplate.opsForZSet().add(key, ids.get(i), scores.get(i));
        }
        return Result.ok(MessageConstant.FOLLOW_SUCCESS, "");
    }

    @Override
    public Result cancelFollow(Long id) {
        if (id == null) {
            return Result.error(MessageConstant.ID_IS_NULL);
        }
        // 获取当前用户id
        Long userId = userIdUtil.getUserId();
        int delete = followMapper.delete(
                new LambdaQueryWrapper<Follow>().eq(Follow::getFolloweeId, id)
                        .eq(Follow::getFollowerId, userId));
        if (delete == 0) {
            return Result.error(MessageConstant.USER_NOT_FOLLOWED, List.of());
        }

        if (Objects.equals(userId, id)) {
            return Result.error(MessageConstant.USER_CANNOT_FOLLOW_SELF);
        }
        // 粉丝数减一
        Long increment = redisTemplate.opsForValue().increment(KeyConstant.FOLLOW_COUNT_KEY + id, -1);
        redisTemplate.opsForZSet().remove(KeyConstant.FANS_LIST_KEY + id, userId);
        redisTemplate.opsForZSet().remove(KeyConstant.Follow_LIST_KEY + userId, id);
        // 更新 ElasticSearch
        User user = elasticsearchOperations.get(id.toString(), User.class);
        if (user != null) {
            if (increment != null) {
                user.setCount(increment.intValue());
                elasticsearchOperations.save(user);
            }
        }
        // 将要取关的用户的帖子查找出来
        Set<ZSetOperations.TypedTuple<Object>> typedTuples =
                redisTemplate.opsForZSet().rangeWithScores(KeyConstant.POST_KEY + id, 0, -1);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok(MessageConstant.UN_FOLLOW_SUCCESS, "");
        }
        List<Object> ids = typedTuples.stream()
                .map(ZSetOperations.TypedTuple::getValue).toList();
        // 删除要取关用户的帖子
        String key = KeyConstant.POST_LIST_KEY + userId;
        for (Object o : ids) {
            redisTemplate.opsForZSet().remove(key, o);
        }
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
        Long total = redisTemplate.opsForZSet().size(KeyConstant.FANS_LIST_KEY + id);
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
        Set<Object> follower = redisTemplate.opsForZSet().range(KeyConstant.FANS_LIST_KEY + id, start, end);
        if (follower == null || follower.isEmpty()) {
            return Result.ok(List.of(), 0L);
        }
        List<Long> ids = follower.stream().map(member -> Long.valueOf(member.toString())).toList();
        // 获取当前用户
        Long userId = userIdUtil.getUserId();
        List<User> users = userMapper.selectByIds(ids);
        List<UserVO> userVOs = users.stream().map(user -> {
            UserVO userVO = BeanUtil.copyProperties(user, UserVO.class);
            Integer count = (Integer) redisTemplate.opsForValue().get(KeyConstant.FOLLOW_COUNT_KEY + user.getId());
            userVO.setCount(count == null ? 0 : count);
            if (userId != null) {
                Double score = redisTemplate.opsForZSet().score(KeyConstant.Follow_LIST_KEY + userId, user.getId());
                userVO.setFollowed(score != null);
            } else {
                userVO.setFollowed(false);
            }
            return userVO;
        }).toList();
        return Result.ok(userVOs, total);
    }


    @Override
    public Result getFolloweeList(Long id, Integer pageNum, Integer pageSize) {
        if (id == null) {
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
        Long total = redisTemplate.opsForZSet().size(KeyConstant.Follow_LIST_KEY + id);
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
        Set<Object> followee = redisTemplate.opsForZSet().range(KeyConstant.Follow_LIST_KEY + id, start, end);
        if (followee == null || followee.isEmpty()) {
            return Result.ok(List.of(), 0L);
        }
        List<Long> ids = followee.stream().map(member -> Long.valueOf(member.toString())).toList();
        List<User> users = userMapper.selectByIds(ids);
        List<UserVO> userVOs = new ArrayList<>();
        for (User user : users) {
            UserVO userVO = BeanUtil.copyProperties(user, UserVO.class);
            userVO.setFollowed(true);
            Integer count = (Integer) redisTemplate.opsForValue().get(KeyConstant.FOLLOW_COUNT_KEY + user.getId());
            userVO.setCount(count == null ? 0 : count);
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
        Long total = redisTemplate.opsForZSet().intersectAndStore(
                KeyConstant.FANS_LIST_KEY + userId,
                KeyConstant.Follow_LIST_KEY + userId,
                KeyConstant.FRIEND_LIST_KEY + userId);
        Set<Object> friend = redisTemplate.opsForZSet().range(KeyConstant.FRIEND_LIST_KEY + userId, start, end);
        if (friend == null || friend.isEmpty()) {
            return Result.ok(List.of(), 0L);
        }
        List<Long> ids = friend.stream().map(member -> Long.valueOf(member.toString())).toList();
        List<User> users = userMapper.selectByIds(ids);
        List<UserVO> userVOs = new ArrayList<>();
        for (User user : users) {
            UserVO userVO = BeanUtil.copyProperties(user, UserVO.class);
            userVO.setFollowed(true);
            Integer count = (Integer) redisTemplate.opsForValue().get(KeyConstant.FOLLOW_COUNT_KEY + user.getId());
            userVO.setCount(count == null ? 0 : count);
            userVOs.add(userVO);
        }
        return Result.ok(userVOs, total);
    }
}
