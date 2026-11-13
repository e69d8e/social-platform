package com.li.socialplatform.recommend;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 首页多因子推荐精排打分器（纯内存无状态逻辑）：
 * <ul>
 *   <li>严格过滤私密、封禁、封禁作者、黑名单及用户负反馈屏蔽内容</li>
 *   <li>结合内容互动质量、发布时间衰减、分类兴趣、标签共振与关注作者关系打分</li>
 *   <li>支持「热门精选」重力衰减榜单与确定性随机扰动（保证同会话分页稳定、换一换常新）</li>
 * </ul>
 */
@Component
public class RecommendScorer {

    /**
     * 过滤不可展示或用户明确屏蔽的候选帖
     */
    public boolean isEligible(CandidatePost post, UserRecommendProfile profile, FeedMode mode) {
        if (post == null || post.getId() == null) {
            return false;
        }
        if (!Boolean.TRUE.equals(post.getEnabled())
                || Boolean.TRUE.equals(post.getIsPrivate())
                || !Boolean.TRUE.equals(post.getAuthorEnabled())) {
            return false;
        }
        if (profile == null) {
            return true;
        }
        Long authorId = post.getUserId();
        if (authorId != null && profile.getHiddenUserIds().contains(authorId)) {
            return false;
        }
        FeedbackSets feedback = profile.getFeedbackSets();
        if (feedback != null) {
            if (feedback.getDislikedPostIds().contains(post.getId())) {
                return false;
            }
            if (authorId != null && feedback.getDislikedAuthorIds().contains(authorId)) {
                return false;
            }
            if (mode == FeedMode.RECOMMEND) {
                if (post.getCategoryId() != null && feedback.getDislikedCategoryIds().contains(post.getCategoryId())) {
                    return false;
                }
                if (post.getTagIds() != null && !feedback.getDislikedTagIds().isEmpty()) {
                    for (Long tagId : post.getTagIds()) {
                        if (feedback.getDislikedTagIds().contains(tagId)) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    /**
     * 批量过滤并对候选集合打分排序（再交由 DiversityReranker 做多样性重排）
     */
    public List<ScoredPost> scoreAll(
            Collection<CandidatePost> candidates,
            UserRecommendProfile profile,
            FeedMode mode,
            LocalDateTime now,
            long seed) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        LocalDateTime safeNow = now == null ? LocalDateTime.now() : now;
        UserRecommendProfile safeProfile = profile == null ? new UserRecommendProfile() : profile;

        double maxCategoryScore = safeProfile.getDecayedCategoryScores().values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);
        double maxTagScore = safeProfile.getDecayedTagScores().values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);

        // 用户前 2 大主导分类，用于识别「拓展发现」探索内容
        Set<Integer> dominantCategories = safeProfile.getDecayedCategoryScores().entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .limit(2)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        List<ScoredPost> scored = new ArrayList<>();
        for (CandidatePost post : candidates) {
            if (!isEligible(post, safeProfile, mode)) {
                continue;
            }
            scored.add(scoreSingle(post, safeProfile, mode, safeNow, seed, maxCategoryScore, maxTagScore, dominantCategories));
        }

        scored.sort(Comparator.comparingDouble(ScoredPost::getScore).reversed()
                .thenComparing(s -> s.getPost().getId(), Comparator.reverseOrder()));
        return scored;
    }

    private ScoredPost scoreSingle(
            CandidatePost post,
            UserRecommendProfile profile,
            FeedMode mode,
            LocalDateTime now,
            long seed,
            double maxCategoryScore,
            double maxTagScore,
            Set<Integer> dominantCategories) {
        int likes = safeNonNegative(post.getLikeCount());
        int stars = safeNonNegative(post.getStarCount());
        int comments = safeNonNegative(post.getCommentCount());
        int views = safeNonNegative(post.getViewCount());

        double interactions = likes * 3.0 + stars * 4.5 + comments * 5.0 + Math.log1p(views) * 1.2;
        double qualityScore = Math.min(1.0, Math.log1p(interactions) / Math.log1p(450.0));

        double ageDays = computeAgeDays(post.getCreateTime(), now);
        double ageHours = ageDays * 24.0;
        double freshnessScore = 1.0 / (1.0 + Math.pow(ageDays / 14.0, 0.85));

        double jitter = deterministicJitter(seed, post.getId());
        boolean isSeen = profile.getSeenPostIds() != null && profile.getSeenPostIds().contains(post.getId());

        if (mode == FeedMode.HOT) {
            // 热门榜采用天尺度重力衰减：既兼顾近期爆款，也保留历史高口碑内容区分度
            double hotScore = (interactions + 1.0) / Math.pow(ageDays + 2.0, 1.2);
            if (seed != 0) {
                hotScore *= (1.0 + 0.04 * (jitter - 0.5));
            }
            if (isSeen) {
                hotScore *= 0.65;
            }
            String reason = interactions >= 120.0 ? "热度飙升" : "热门精选";
            return ScoredPost.builder()
                    .post(post)
                    .score(hotScore)
                    .qualityScore(qualityScore)
                    .freshnessScore(freshnessScore)
                    .seen(isSeen)
                    .exploration(false)
                    .reason(reason)
                    .build();
        }

        double categoryAffinity = 0.0;
        if (maxCategoryScore > 0.0 && post.getCategoryId() != null) {
            double rawCat = profile.getDecayedCategoryScores().getOrDefault(post.getCategoryId(), 0.0);
            if (rawCat > 0.0) {
                categoryAffinity = Math.sqrt(Math.min(1.0, rawCat / maxCategoryScore));
            }
        }

        double tagAffinity = 0.0;
        String matchedTagName = null;
        if (maxTagScore > 0.0 && post.getTagIds() != null && !post.getTagIds().isEmpty()) {
            double bestTagNorm = 0.0;
            double sumTagNorm = 0.0;
            int matchedCount = 0;
            Long bestTagId = null;
            for (Long tagId : post.getTagIds()) {
                double rawTag = profile.getDecayedTagScores().getOrDefault(tagId, 0.0);
                if (rawTag > 0.0) {
                    double norm = Math.sqrt(Math.min(1.0, rawTag / maxTagScore));
                    sumTagNorm += norm;
                    matchedCount++;
                    if (norm > bestTagNorm) {
                        bestTagNorm = norm;
                        bestTagId = tagId;
                    }
                }
            }
            if (matchedCount > 0) {
                double avgTagNorm = sumTagNorm / post.getTagIds().size();
                tagAffinity = 0.70 * bestTagNorm + 0.30 * avgTagNorm;
                if (bestTagId != null && post.getTagNamesById() != null) {
                    matchedTagName = post.getTagNamesById().get(bestTagId);
                }
            }
        }

        double authorAffinity = (post.getUserId() != null && profile.getFollowedUserIds().contains(post.getUserId()))
                ? 1.0
                : 0.0;

        boolean isExploration = profile.hasPersonalSignals()
                && !dominantCategories.isEmpty()
                && post.getCategoryId() != null
                && !dominantCategories.contains(post.getCategoryId())
                && qualityScore >= 0.30;

        double totalScore;
        if (profile.hasPersonalSignals()) {
            totalScore = 0.28 * categoryAffinity
                    + 0.26 * tagAffinity
                    + 0.14 * authorAffinity
                    + 0.20 * qualityScore
                    + 0.12 * freshnessScore
                    + 0.08 * jitter;
        } else {
            // 游客或冷启动新用户：以高质量口碑与新鲜度为主，辅以换一换探索扰动
            totalScore = 0.50 * qualityScore
                    + 0.36 * freshnessScore
                    + 0.14 * jitter;
        }

        if (isSeen) {
            totalScore -= 1.0;
        }

        String reason = buildRecommendReason(
                post,
                mode,
                authorAffinity,
                tagAffinity,
                matchedTagName,
                categoryAffinity,
                qualityScore,
                ageHours,
                isExploration);

        return ScoredPost.builder()
                .post(post)
                .score(totalScore)
                .qualityScore(qualityScore)
                .freshnessScore(freshnessScore)
                .categoryAffinity(categoryAffinity)
                .tagAffinity(tagAffinity)
                .authorAffinity(authorAffinity)
                .seen(isSeen)
                .exploration(isExploration)
                .reason(reason)
                .build();
    }

    private String buildRecommendReason(
            CandidatePost post,
            FeedMode mode,
            double authorAffinity,
            double tagAffinity,
            String matchedTagName,
            double categoryAffinity,
            double qualityScore,
            double ageHours,
            boolean isExploration) {
        if (mode == FeedMode.LATEST) {
            return null;
        }
        if (authorAffinity > 0.0) {
            return "关注作者";
        }
        if (tagAffinity >= 0.40 && matchedTagName != null && !matchedTagName.isBlank()) {
            return "偏爱 #" + matchedTagName;
        }
        if (categoryAffinity >= 0.35 && post.getCategoryName() != null && !post.getCategoryName().isBlank()) {
            return "常看 " + post.getCategoryName();
        }
        if (isExploration && qualityScore >= 0.45) {
            return "拓展发现";
        }
        if (qualityScore >= 0.55) {
            return "高赞精选";
        }
        if (ageHours <= 72.0) {
            return "最新发布";
        }
        return "猜你喜欢";
    }

    public static double computeAgeDays(LocalDateTime createTime, LocalDateTime now) {
        if (createTime == null || now == null) {
            return 30.0;
        }
        long minutes = Duration.between(createTime, now).toMinutes();
        return Math.max(0.0, minutes / 1440.0);
    }

    /**
     * 基于 SplitMix64 混洗生成的确定性伪随机扰动 [0.0, 1.0)
     */
    public static double deterministicJitter(long seed, Long postId) {
        long z = (seed ^ (postId == null ? 0L : postId * 0x9E3779B97F4A7C15L)) + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return (z & 0xFFFFL) / 65536.0;
    }

    private static int safeNonNegative(Integer v) {
        return v == null || v < 0 ? 0 : v;
    }
}
