package com.li.socialplatform.recommend;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RecommendScorerTest {

    private final RecommendScorer scorer = new RecommendScorer();
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 13, 12, 0);

    private CandidatePost basePost(long id, long authorId, int categoryId, int likes, int daysAgo) {
        return CandidatePost.builder()
                .id(id)
                .userId(authorId)
                .categoryId(categoryId)
                .categoryName("分类" + categoryId)
                .likeCount(likes)
                .starCount(likes / 3)
                .commentCount(likes / 5)
                .viewCount(likes * 20)
                .createTime(now.minusDays(daysAgo))
                .enabled(true)
                .isPrivate(false)
                .authorEnabled(true)
                .build();
    }

    @Test
    void filtersDisabledPrivateBlacklistedAndNegativeFeedbackPosts() {
        CandidatePost valid = basePost(1L, 10L, 2, 20, 2);
        CandidatePost disabled = basePost(2L, 10L, 2, 20, 2);
        disabled.setEnabled(false);
        CandidatePost priv = basePost(3L, 10L, 2, 20, 2);
        priv.setIsPrivate(true);
        CandidatePost bannedAuthor = basePost(4L, 11L, 2, 20, 2);
        bannedAuthor.setAuthorEnabled(false);
        CandidatePost blacklistedAuthor = basePost(5L, 12L, 2, 20, 2);
        CandidatePost dislikedPost = basePost(6L, 13L, 2, 20, 2);
        CandidatePost dislikedAuthor = basePost(7L, 14L, 2, 20, 2);
        CandidatePost dislikedCategory = basePost(8L, 15L, 9, 20, 2);
        CandidatePost dislikedTag = basePost(9L, 16L, 3, 20, 2);
        dislikedTag.setTagIds(List.of(99L));

        UserRecommendProfile profile = UserRecommendProfile.builder()
                .userId(1L)
                .hiddenUserIds(Set.of(12L))
                .feedbackSets(FeedbackSets.builder()
                        .dislikedPostIds(Set.of(6L))
                        .dislikedAuthorIds(Set.of(14L))
                        .dislikedCategoryIds(Set.of(9))
                        .dislikedTagIds(Set.of(99L))
                        .build())
                .build();

        List<ScoredPost> recommendScored = scorer.scoreAll(
                List.of(valid, disabled, priv, bannedAuthor, blacklistedAuthor,
                        dislikedPost, dislikedAuthor, dislikedCategory, dislikedTag),
                profile, FeedMode.RECOMMEND, now, 100L);
        assertEquals(1, recommendScored.size());
        assertEquals(1L, recommendScored.getFirst().getPost().getId());
    }

    @Test
    void ranksPersonalizedCategoryTagAndFollowHigherThanUnrelated() {
        CandidatePost followedPost = basePost(101L, 20L, 2, 15, 3);
        CandidatePost tagMatchedPost = basePost(102L, 21L, 3, 18, 3);
        tagMatchedPost.setCategoryName("AI");
        tagMatchedPost.setTagIds(List.of(6L));
        tagMatchedPost.setTagNamesById(Map.of(6L, "大语言模型"));
        CandidatePost unrelatedPost = basePost(103L, 22L, 6, 18, 3);

        UserRecommendProfile profile = UserRecommendProfile.builder()
                .userId(1L)
                .decayedCategoryScores(Map.of(3, 80.0))
                .decayedTagScores(Map.of(6L, 95.0))
                .followedUserIds(Set.of(20L))
                .build();

        List<ScoredPost> scored = scorer.scoreAll(
                List.of(unrelatedPost, tagMatchedPost, followedPost),
                profile, FeedMode.RECOMMEND, now, 42L);

        assertEquals(3, scored.size());
        assertTrue(scored.get(0).getPost().getId() == 102L || scored.get(0).getPost().getId() == 101L);
        assertEquals(103L, scored.get(2).getPost().getId());

        Map<Long, String> reasons = scored.stream()
                .collect(java.util.stream.Collectors.toMap(s -> s.getPost().getId(), ScoredPost::getReason));
        assertEquals("关注作者", reasons.get(101L));
        assertEquals("偏爱 #大语言模型", reasons.get(102L));
    }

    @Test
    void seenPostsAreDemotedBelowUnseenOnRefresh() {
        CandidatePost seenHighLike = basePost(201L, 30L, 3, 200, 1);
        CandidatePost unseenModerate = basePost(202L, 31L, 3, 20, 2);

        UserRecommendProfile profile = UserRecommendProfile.builder()
                .userId(1L)
                .decayedCategoryScores(Map.of(3, 50.0))
                .seenPostIds(Set.of(201L))
                .build();

        List<ScoredPost> scored = scorer.scoreAll(
                List.of(seenHighLike, unseenModerate),
                profile, FeedMode.RECOMMEND, now, 777L);

        assertEquals(202L, scored.get(0).getPost().getId());
        assertFalse(scored.get(0).isSeen());
        assertEquals(201L, scored.get(1).getPost().getId());
        assertTrue(scored.get(1).isSeen());
    }

    @Test
    void hotModeBalancesEngagementAndAgeDecayAcrossLongAndShortWindows() {
        CandidatePost recentPopular = basePost(301L, 40L, 4, 80, 1);
        CandidatePost staleSimilar = basePost(302L, 41L, 4, 80, 100);

        List<ScoredPost> hot = scorer.scoreAll(
                List.of(staleSimilar, recentPopular),
                new UserRecommendProfile(),
                FeedMode.HOT,
                now,
                0L);

        assertEquals(301L, hot.get(0).getPost().getId());
        assertTrue(hot.get(0).getScore() > hot.get(1).getScore() * 5.0);
    }

    @Test
    void deterministicJitterIsStableForSameSeed() {
        double a1 = RecommendScorer.deterministicJitter(12345L, 99L);
        double a2 = RecommendScorer.deterministicJitter(12345L, 99L);
        double b = RecommendScorer.deterministicJitter(54321L, 99L);
        assertEquals(a1, a2);
        assertTrue(a1 >= 0.0 && a1 < 1.0);
        assertNotEquals(a1, b);
    }
}
