package com.li.socialplatform.server.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.socialplatform.common.constant.CacheTtl;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.common.constant.MessageConstant;
import com.li.socialplatform.common.utils.RedisUtil;
import com.li.socialplatform.common.utils.UserIdUtil;
import com.li.socialplatform.common.utils.UserIntersetScoreUtil;
import com.li.socialplatform.pojo.dto.RecommendFeedbackDTO;
import com.li.socialplatform.pojo.entity.Post;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.pojo.entity.UserRecommendFeedback;
import com.li.socialplatform.recommend.FeedbackSets;
import com.li.socialplatform.server.mapper.PostMapper;
import com.li.socialplatform.server.mapper.UserRecommendFeedbackMapper;
import com.li.socialplatform.server.service.IRecommendFeedbackService;
import com.li.socialplatform.server.service.ITagService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RecommendFeedbackServiceImpl
        extends ServiceImpl<UserRecommendFeedbackMapper, UserRecommendFeedback>
        implements IRecommendFeedbackService {

    private static final Set<String> VALID_TYPES = Set.of(
            UserRecommendFeedback.TYPE_POST,
            UserRecommendFeedback.TYPE_CATEGORY,
            UserRecommendFeedback.TYPE_TAG,
            UserRecommendFeedback.TYPE_AUTHOR
    );

    private final UserRecommendFeedbackMapper feedbackMapper;
    private final PostMapper postMapper;
    private final ITagService tagService;
    private final UserIntersetScoreUtil userIntersetScoreUtil;
    private final UserIdUtil userIdUtil;
    private final RedisUtil redisUtil;

    @Override
    public Result submitFeedback(RecommendFeedbackDTO dto) {
        Long userId = userIdUtil.getUserId();
        if (userId == null) {
            return Result.error(MessageConstant.USER_NOT_LOGIN);
        }
        String type = dto.getTargetType().trim().toUpperCase(Locale.ROOT);
        if (!VALID_TYPES.contains(type)) {
            return Result.error(MessageConstant.FEEDBACK_INVALID_TYPE);
        }

        UserRecommendFeedback existing = feedbackMapper.selectOne(new LambdaQueryWrapper<UserRecommendFeedback>()
                .eq(UserRecommendFeedback::getUserId, userId)
                .eq(UserRecommendFeedback::getTargetType, type)
                .eq(UserRecommendFeedback::getTargetId, dto.getTargetId())
                .last("LIMIT 1"));
        if (existing == null) {
            UserRecommendFeedback row = new UserRecommendFeedback();
            row.setUserId(userId);
            row.setTargetType(type);
            row.setTargetId(dto.getTargetId());
            row.setFeedbackType(UserRecommendFeedback.FEEDBACK_DISLIKE);
            row.setReason(dto.getReason());
            try {
                feedbackMapper.insert(row);
            } catch (DuplicateKeyException ignored) {
                // 并发幂等
            }
        } else if (StringUtils.isNotBlank(dto.getReason())) {
            existing.setReason(dto.getReason());
            feedbackMapper.updateById(existing);
        }

        applyInterestPenalty(userId, type, dto.getTargetId());
        evictCache(userId);
        return Result.ok(MessageConstant.FEEDBACK_SUCCESS, "");
    }

    @Override
    public Result removeFeedback(String targetType, Long targetId) {
        Long userId = userIdUtil.getUserId();
        if (userId == null) {
            return Result.error(MessageConstant.USER_NOT_LOGIN);
        }
        if (StringUtils.isBlank(targetType) || targetId == null) {
            return Result.error(MessageConstant.ID_IS_NULL);
        }
        String type = targetType.trim().toUpperCase(Locale.ROOT);
        if (!VALID_TYPES.contains(type)) {
            return Result.error(MessageConstant.FEEDBACK_INVALID_TYPE);
        }
        feedbackMapper.delete(new LambdaQueryWrapper<UserRecommendFeedback>()
                .eq(UserRecommendFeedback::getUserId, userId)
                .eq(UserRecommendFeedback::getTargetType, type)
                .eq(UserRecommendFeedback::getTargetId, targetId));
        evictCache(userId);
        return Result.ok(MessageConstant.FEEDBACK_UNDO_SUCCESS, "");
    }

    @Override
    public FeedbackSets getFeedbackSets(Long userId) {
        if (userId == null) {
            return FeedbackSets.empty();
        }
        String cacheKey = KeyConstant.RECOMMEND_FEEDBACK_KEY + userId;
        String cachedJson = redisUtil.strings().opsForValue().get(cacheKey);
        if (StringUtils.isNotBlank(cachedJson)) {
            return JSON.parseObject(cachedJson, FeedbackSets.class);
        }

        List<UserRecommendFeedback> rows = feedbackMapper.selectList(
                new LambdaQueryWrapper<UserRecommendFeedback>().eq(UserRecommendFeedback::getUserId, userId));
        Set<Long> posts = new HashSet<>();
        Set<Integer> categories = new HashSet<>();
        Set<Long> tags = new HashSet<>();
        Set<Long> authors = new HashSet<>();
        for (UserRecommendFeedback row : rows) {
            switch (row.getTargetType()) {
                case UserRecommendFeedback.TYPE_POST -> posts.add(row.getTargetId());
                case UserRecommendFeedback.TYPE_CATEGORY -> categories.add(row.getTargetId().intValue());
                case UserRecommendFeedback.TYPE_TAG -> tags.add(row.getTargetId());
                case UserRecommendFeedback.TYPE_AUTHOR -> authors.add(row.getTargetId());
                default -> { }
            }
        }
        FeedbackSets sets = FeedbackSets.builder()
                .dislikedPostIds(posts)
                .dislikedCategoryIds(categories)
                .dislikedTagIds(tags)
                .dislikedAuthorIds(authors)
                .build();
        redisUtil.strings().opsForValue().set(
                cacheKey,
                JSON.toJSONString(sets),
                CacheTtl.RECOMMEND_FEEDBACK_DAYS,
                TimeUnit.DAYS);
        return sets;
    }

    private void applyInterestPenalty(Long userId, String type, Long targetId) {
        switch (type) {
            case UserRecommendFeedback.TYPE_POST -> {
                Post post = postMapper.selectById(targetId);
                if (post != null) {
                    List<Long> tagIds = tagService.getTagIdsByPostIds(List.of(targetId))
                            .getOrDefault(targetId, List.of());
                    userIntersetScoreUtil.recordPostInteraction(userId, post.getCategoryId(), tagIds, -3, -4);
                }
            }
            case UserRecommendFeedback.TYPE_CATEGORY ->
                    userIntersetScoreUtil.resetCategoryScore(userId, targetId.intValue());
            case UserRecommendFeedback.TYPE_TAG ->
                    userIntersetScoreUtil.resetTagScore(userId, targetId);
            default -> { }
        }
    }

    private void evictCache(Long userId) {
        redisUtil.delete(KeyConstant.RECOMMEND_FEEDBACK_KEY + userId);
        try {
            Set<String> snapshotKeys = redisUtil.scan(KeyConstant.RECOMMEND_SNAPSHOT_KEY + "u:" + userId + ":*");
            if (!snapshotKeys.isEmpty()) {
                redisUtil.delete(snapshotKeys);
            }
        } catch (Exception ignored) {
            // 扫描清理快照失败不影响主流程
        }
    }
}
