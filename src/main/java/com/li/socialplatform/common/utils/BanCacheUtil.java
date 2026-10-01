package com.li.socialplatform.common.utils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.socialplatform.common.constant.CacheTtl;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.pojo.entity.BanRecord;
import com.li.socialplatform.server.mapper.BanRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 封禁数据（用户封禁/审核员封帖）的统一缓存组件，Cache-Aside + 空结果防穿透
 *
 * @author e69d8e
 * @since 2026/05/23
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BanCacheUtil {

    private final RedisUtil redisUtil;
    private final BanRecordMapper banRecordMapper;

    // ==================== 用户封禁 ====================

    public void addBanUser(Long adminId, Long targetUserId) {
        banRecordMapper.insert(new BanRecord(null, adminId, targetUserId, 0, null));
        redisUtil.zsets().add(KeyConstant.BAN_USER_KEY, targetUserId, System.currentTimeMillis());
        redisUtil.expire(KeyConstant.BAN_USER_KEY, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
    }

    public void removeBanUser(Long targetUserId) {
        banRecordMapper.delete(new LambdaQueryWrapper<BanRecord>()
                .eq(BanRecord::getTargetId, targetUserId)
                .eq(BanRecord::getType, 0));
        redisUtil.zsets().remove(KeyConstant.BAN_USER_KEY, targetUserId);
    }

    public Long getBanUserTotal() {
        Long size = redisUtil.zsets().size(KeyConstant.BAN_USER_KEY);
        if (size != null && size > 0) {
            return size;
        }
        ensureBanUsersLoaded();
        Long reloaded = redisUtil.zsets().size(KeyConstant.BAN_USER_KEY);
        return reloaded != null ? reloaded : 0L;
    }

    public Set<Object> getBanUserIds(long start, long end) {
        Set<Object> members = redisUtil.zsets().range(KeyConstant.BAN_USER_KEY, start, end);
        if (members != null && !members.isEmpty()) {
            return members;
        }
        ensureBanUsersLoaded();
        return redisUtil.zsets().range(KeyConstant.BAN_USER_KEY, start, end);
    }

    private void ensureBanUsersLoaded() {
        // 空结果标记：DB 无封禁数据时不反复打 DB
        if (Boolean.TRUE.equals(redisUtil.hasKey(KeyConstant.BAN_USER_EMPTY_KEY))) {
            return;
        }
        List<BanRecord> records = banRecordMapper.selectList(
                new LambdaQueryWrapper<BanRecord>().eq(BanRecord::getType, 0));
        if (records.isEmpty()) {
            redisUtil.set(KeyConstant.BAN_USER_EMPTY_KEY, 1,
                    CacheTtl.EMPTY_MARKER_MINUTES, TimeUnit.MINUTES);
            return;
        }
        Map<Object, Double> memberScores = records.stream().collect(Collectors.toMap(
                r -> (Object) r.getTargetId(), BanCacheUtil::toCreateTimeScore, (a, b) -> a));
        redisUtil.zAddAll(KeyConstant.BAN_USER_KEY, memberScores);
        redisUtil.expire(KeyConstant.BAN_USER_KEY, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
        log.debug("从DB加载封禁用户数据: {} 条", records.size());
    }

    // ==================== 帖子封禁 ====================

    public void addBanPost(Long reviewerId, Long postId) {
        banRecordMapper.insert(new BanRecord(null, reviewerId, postId, 1, null));
        String key = KeyConstant.BAN_POST_KEY + reviewerId;
        redisUtil.zsets().add(key, postId, System.currentTimeMillis());
        redisUtil.expire(key, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
    }

    public void removeBanPost(Long reviewerId, Long postId) {
        banRecordMapper.delete(new LambdaQueryWrapper<BanRecord>()
                .eq(BanRecord::getUserId, reviewerId)
                .eq(BanRecord::getTargetId, postId)
                .eq(BanRecord::getType, 1));
        redisUtil.zsets().remove(KeyConstant.BAN_POST_KEY + reviewerId, postId);
    }

    public Long getBanPostTotal(Long reviewerId) {
        String key = KeyConstant.BAN_POST_KEY + reviewerId;
        Long size = redisUtil.zsets().size(key);
        if (size != null && size > 0) {
            return size;
        }
        ensureBanPostsLoaded(reviewerId);
        Long reloaded = redisUtil.zsets().size(key);
        return reloaded != null ? reloaded : 0L;
    }

    public Set<Object> getBanPostIds(Long reviewerId, long start, long end) {
        String key = KeyConstant.BAN_POST_KEY + reviewerId;
        Set<Object> members = redisUtil.zsets().range(key, start, end);
        if (members != null && !members.isEmpty()) {
            return members;
        }
        ensureBanPostsLoaded(reviewerId);
        return redisUtil.zsets().range(key, start, end);
    }

    private void ensureBanPostsLoaded(Long reviewerId) {
        String emptyKey = KeyConstant.BAN_POST_EMPTY_KEY + reviewerId;
        if (Boolean.TRUE.equals(redisUtil.hasKey(emptyKey))) {
            return;
        }
        List<BanRecord> records = banRecordMapper.selectList(
                new LambdaQueryWrapper<BanRecord>()
                        .eq(BanRecord::getUserId, reviewerId)
                        .eq(BanRecord::getType, 1));
        if (records.isEmpty()) {
            redisUtil.set(emptyKey, 1, CacheTtl.EMPTY_MARKER_MINUTES, TimeUnit.MINUTES);
            return;
        }
        String key = KeyConstant.BAN_POST_KEY + reviewerId;
        Map<Object, Double> memberScores = records.stream().collect(Collectors.toMap(
                r -> (Object) r.getTargetId(), BanCacheUtil::toCreateTimeScore, (a, b) -> a));
        redisUtil.zAddAll(key, memberScores);
        redisUtil.expire(key, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
        log.debug("从DB加载审查员 {} 封禁帖子数据: {} 条", reviewerId, records.size());
    }

    private static double toCreateTimeScore(BanRecord record) {
        return record.getCreateTime() != null
                ? record.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                : (double) System.currentTimeMillis();
    }
}
