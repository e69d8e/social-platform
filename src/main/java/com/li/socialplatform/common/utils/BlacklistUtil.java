package com.li.socialplatform.common.utils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.socialplatform.common.constant.CacheTtl;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.pojo.entity.UserBlacklist;
import com.li.socialplatform.server.mapper.UserBlacklistMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 用户黑名单缓存组件（Cache-Aside）。
 *
 * <p>黑名单是**对称**语义：A 拉黑 B 后，A 看不到 B 的内容，B 也看不到 A 的内容。
 * 因此缓存维护两个方向的 Set：</p>
 * <ul>
 *   <li>{@code blacklist:{uid}}：uid 拉黑的人</li>
 *   <li>{@code blocked-by:{uid}}：拉黑 uid 的人</li>
 * </ul>
 *
 * <p>读取期过滤是唯一权威：所有帖子/用户/消息的读取面都通过 {@link #hiddenUserIds} 或
 * {@link #isHidden} 现取现判，所以拉黑/解除后无需清理任何信息流缓存也不会漏内容。</p>
 *
 * @author e69d8e
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BlacklistUtil {

    private final RedisUtil redisUtil;
    private final UserBlacklistMapper userBlacklistMapper;

    /**
     * 与 viewer 互相不可见的用户集合（我拉黑的 ∪ 拉黑我的）。
     * 未登录/无黑名单时返回空集合，调用方无需判空。
     */
    public Set<Long> hiddenUserIds(Long viewerId) {
        if (viewerId == null) {
            return Set.of();
        }
        ensureLoaded(viewerId);
        Set<Long> result = new HashSet<>();
        collect(redisUtil.sets().members(KeyConstant.USER_BLACKLIST_KEY + viewerId), result);
        collect(redisUtil.sets().members(KeyConstant.USER_BLOCKED_BY_KEY + viewerId), result);
        return result;
    }

    /**
     * viewer 是否与 author 互相不可见（单人判定，用于详情页与写操作拦截）
     */
    public boolean isHidden(Long viewerId, Long authorId) {
        if (viewerId == null || authorId == null || Objects.equals(viewerId, authorId)) {
            return false;
        }
        ensureLoaded(viewerId);
        return Boolean.TRUE.equals(redisUtil.sets()
                .isMember(KeyConstant.USER_BLACKLIST_KEY + viewerId, authorId))
                || Boolean.TRUE.equals(redisUtil.sets()
                .isMember(KeyConstant.USER_BLOCKED_BY_KEY + viewerId, authorId));
    }

    /**
     * 我是否拉黑了对方（用于主页「你已拉黑 TA」横幅与解除入口）。
     * 注意与 {@link #isHidden} 的区别：后者是双向不可见，前者只表达我主动拉黑。
     */
    public boolean isBlockedByMe(Long viewerId, Long targetId) {
        if (viewerId == null || targetId == null || Objects.equals(viewerId, targetId)) {
            return false;
        }
        ensureLoaded(viewerId);
        return Boolean.TRUE.equals(redisUtil.sets()
                .isMember(KeyConstant.USER_BLACKLIST_KEY + viewerId, targetId));
    }

    /**
     * 拉黑成功后同步缓存（两个方向都要写，保证双方视角立即生效）
     */
    public void addRelation(Long userId, Long blockedUserId) {
        redisUtil.sets().add(KeyConstant.USER_BLACKLIST_KEY + userId, blockedUserId);
        redisUtil.sets().add(KeyConstant.USER_BLOCKED_BY_KEY + blockedUserId, userId);
        redisUtil.expire(KeyConstant.USER_BLACKLIST_KEY + userId, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
        redisUtil.expire(KeyConstant.USER_BLOCKED_BY_KEY + blockedUserId, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
        // 双方此前可能处于「空黑名单」标记状态，写完后标记与集合一致，无需额外处理
        redisUtil.set(KeyConstant.BLACKLIST_LOADED_KEY + userId, 1, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
        redisUtil.set(KeyConstant.BLACKLIST_LOADED_KEY + blockedUserId, 1, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
    }

    /**
     * 解除拉黑后同步缓存
     */
    public void removeRelation(Long userId, Long blockedUserId) {
        redisUtil.sets().remove(KeyConstant.USER_BLACKLIST_KEY + userId, blockedUserId);
        redisUtil.sets().remove(KeyConstant.USER_BLOCKED_BY_KEY + blockedUserId, userId);
    }

    /**
     * 确保该用户的两个方向都已从 DB 加载。
     * 只做「补齐」，不做「覆盖」：增量写入缓存的关系不会被回源覆盖掉。
     */
    private void ensureLoaded(Long userId) {
        String loadedKey = KeyConstant.BLACKLIST_LOADED_KEY + userId;
        if (Boolean.TRUE.equals(redisUtil.hasKey(loadedKey))
                || Boolean.TRUE.equals(redisUtil.hasKey(KeyConstant.USER_BLACKLIST_KEY + userId))
                        && Boolean.TRUE.equals(redisUtil.hasKey(KeyConstant.USER_BLOCKED_BY_KEY + userId))) {
            return;
        }
        List<UserBlacklist> mine = userBlacklistMapper.selectList(
                new LambdaQueryWrapper<UserBlacklist>().eq(UserBlacklist::getUserId, userId));
        List<UserBlacklist> byOthers = userBlacklistMapper.selectList(
                new LambdaQueryWrapper<UserBlacklist>().eq(UserBlacklist::getBlockedUserId, userId));

        for (UserBlacklist row : mine) {
            redisUtil.sets().add(KeyConstant.USER_BLACKLIST_KEY + userId, row.getBlockedUserId());
        }
        for (UserBlacklist row : byOthers) {
            redisUtil.sets().add(KeyConstant.USER_BLOCKED_BY_KEY + userId, row.getUserId());
        }
        boolean empty = mine.isEmpty() && byOthers.isEmpty();
        // 集合为空时用短 TTL 标记防穿透，避免每次请求都打 DB
        if (empty) {
            redisUtil.set(loadedKey, 1, CacheTtl.EMPTY_MARKER_MINUTES, TimeUnit.MINUTES);
        } else {
            redisUtil.expire(KeyConstant.USER_BLACKLIST_KEY + userId, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
            redisUtil.expire(KeyConstant.USER_BLOCKED_BY_KEY + userId, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
            redisUtil.set(loadedKey, 1, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
        }
        log.debug("从DB加载用户 {} 黑名单：拉黑 {} 人，被 {} 人拉黑", userId, mine.size(), byOthers.size());
    }

    private void collect(Collection<Object> members, Set<Long> target) {
        if (members == null) {
            return;
        }
        for (Object member : members) {
            if (member == null) {
                continue;
            }
            try {
                target.add(Long.valueOf(member.toString()));
            } catch (NumberFormatException ignored) {
                // 脏数据忽略
            }
        }
    }
}
