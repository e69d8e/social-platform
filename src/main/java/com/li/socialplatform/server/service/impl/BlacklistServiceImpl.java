package com.li.socialplatform.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.common.constant.MessageConstant;
import com.li.socialplatform.common.utils.AsyncTaskUtil;
import com.li.socialplatform.common.utils.BlacklistUtil;
import com.li.socialplatform.common.utils.RedisUtil;
import com.li.socialplatform.common.utils.UserIdUtil;
import com.li.socialplatform.pojo.entity.Follow;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.pojo.entity.User;
import com.li.socialplatform.pojo.entity.UserBlacklist;
import com.li.socialplatform.pojo.vo.BlacklistItemVO;
import com.li.socialplatform.server.mapper.FollowMapper;
import com.li.socialplatform.server.mapper.UserBlacklistMapper;
import com.li.socialplatform.server.mapper.UserMapper;
import com.li.socialplatform.server.service.IBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 用户黑名单服务实现
 *
 * @author e69d8e
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BlacklistServiceImpl extends ServiceImpl<UserBlacklistMapper, UserBlacklist>
        implements IBlacklistService {

    /** 单个用户黑名单上限：防止刷表与滥用 */
    private static final long MAX_BLACKLIST_SIZE = 500L;

    private final UserBlacklistMapper userBlacklistMapper;
    private final UserMapper userMapper;
    private final FollowMapper followMapper;
    private final UserIdUtil userIdUtil;
    private final RedisUtil redisUtil;
    private final BlacklistUtil blacklistUtil;
    private final AsyncTaskUtil asyncTaskUtil;

    @Transactional
    @Override
    public Result block(Long targetUserId) {
        if (targetUserId == null) {
            return Result.error(MessageConstant.ID_IS_NULL);
        }
        Long userId = userIdUtil.getUserId();
        if (Objects.equals(userId, targetUserId)) {
            return Result.error(MessageConstant.CANNOT_BLOCK_SELF);
        }
        User target = userMapper.selectById(targetUserId);
        if (target == null) {
            return Result.error(MessageConstant.USER_NOT_EXIST);
        }
        Long current = userBlacklistMapper.selectCount(
                new LambdaQueryWrapper<UserBlacklist>().eq(UserBlacklist::getUserId, userId));
        if (current != null && current >= MAX_BLACKLIST_SIZE) {
            return Result.error(MessageConstant.BLACKLIST_FULL);
        }
        try {
            userBlacklistMapper.insert(new UserBlacklist(null, userId, targetUserId, null));
            log.info("用户 {} 拉黑用户 {}", userId, targetUserId);
        } catch (DuplicateKeyException ignored) {
            // 重复拉黑按幂等处理：多端同时点击不应该报错
        }
        blacklistUtil.addRelation(userId, targetUserId);
        // 拉黑即断绝关注关系：保留会让「已关注却看不到内容」的矛盾状态一直存在
        removeFollowRelation(userId, targetUserId);
        removeFollowRelation(targetUserId, userId);
        return Result.ok(MessageConstant.BLOCK_SUCCESS, "");
    }

    @Transactional
    @Override
    public Result unblock(Long targetUserId) {
        if (targetUserId == null) {
            return Result.error(MessageConstant.ID_IS_NULL);
        }
        Long userId = userIdUtil.getUserId();
        userBlacklistMapper.delete(new LambdaQueryWrapper<UserBlacklist>()
                .eq(UserBlacklist::getUserId, userId)
                .eq(UserBlacklist::getBlockedUserId, targetUserId));
        blacklistUtil.removeRelation(userId, targetUserId);
        // 关注关系不自动恢复：是否继续关注应由用户自己决定
        return Result.ok(MessageConstant.UNBLOCK_SUCCESS, "");
    }

    @Override
    public Result list(Integer pageNum, Integer pageSize) {
        Long userId = userIdUtil.getUserId();
        IPage<UserBlacklist> page = userBlacklistMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<UserBlacklist>()
                        .eq(UserBlacklist::getUserId, userId)
                        .orderByDesc(UserBlacklist::getCreateTime));
        List<UserBlacklist> records = page.getRecords();
        if (records.isEmpty()) {
            return Result.ok(new ArrayList<>(), page.getTotal());
        }
        List<Long> ids = records.stream().map(UserBlacklist::getBlockedUserId).toList();
        Map<Long, User> userMap = new HashMap<>();
        userMapper.selectBatchIds(ids).forEach(u -> userMap.put(u.getId(), u));

        List<BlacklistItemVO> items = new ArrayList<>();
        for (UserBlacklist record : records) {
            User user = userMap.get(record.getBlockedUserId());
            if (user == null) {
                // 账号已被物理删除：保留一条占位，便于用户解除残留关系
                items.add(new BlacklistItemVO(record.getBlockedUserId(), null, "已注销用户",
                        null, false, record.getCreateTime()));
                continue;
            }
            items.add(new BlacklistItemVO(user.getId(), user.getUsername(), user.getNickname(),
                    user.getAvatar(), user.getEnabled(), record.getCreateTime()));
        }
        return Result.ok(items, page.getTotal());
    }

    /**
     * 删除单向关注关系（DB + 缓存 + 粉丝数），无关系时静默返回
     */
    private void removeFollowRelation(Long followerId, Long followeeId) {
        int deleted = followMapper.delete(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowerId, followerId)
                .eq(Follow::getFolloweeId, followeeId));
        if (deleted <= 0) {
            return;
        }
        redisUtil.zsets().remove(KeyConstant.FANS_LIST_KEY + followeeId, followerId);
        redisUtil.zsets().remove(KeyConstant.Follow_LIST_KEY + followerId, followeeId);
        // 计数键存在才自减：不存在时让后续回源按 DB 重新计算，避免把计数写成负数
        if (Boolean.TRUE.equals(redisUtil.hasKey(KeyConstant.FOLLOW_COUNT_KEY + followeeId))) {
            Long increment = redisUtil.increment(KeyConstant.FOLLOW_COUNT_KEY + followeeId, -1);
            if (increment != null) {
                asyncTaskUtil.syncUserFansCount(followeeId, increment.intValue());
            }
        }
        log.info("拉黑联动解除关注: {} -> {}", followerId, followeeId);
    }
}
