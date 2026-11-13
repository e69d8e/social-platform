package com.li.socialplatform.recommend;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;

/**
 * 用户实时推荐画像快照
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRecommendProfile {
    private Long userId;
    @Builder.Default
    private Map<Integer, Double> decayedCategoryScores = Map.of();
    @Builder.Default
    private Map<Long, Double> decayedTagScores = Map.of();
    @Builder.Default
    private Set<Long> followedUserIds = Set.of();
    @Builder.Default
    private Set<Long> hiddenUserIds = Set.of();
    @Builder.Default
    private Set<Long> seenPostIds = Set.of();
    @Builder.Default
    private FeedbackSets feedbackSets = FeedbackSets.empty();

    public boolean hasPersonalSignals() {
        return !decayedCategoryScores.isEmpty()
                || !decayedTagScores.isEmpty()
                || !followedUserIds.isEmpty();
    }
}
