package com.li.socialplatform.recommend;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 用户负反馈屏蔽集合（按维度分类，供推荐与榜单过滤使用）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackSets implements Serializable {

    @Builder.Default
    private Set<Long> dislikedPostIds = new HashSet<>();

    @Builder.Default
    private Set<Integer> dislikedCategoryIds = new HashSet<>();

    @Builder.Default
    private Set<Long> dislikedTagIds = new HashSet<>();

    @Builder.Default
    private Set<Long> dislikedAuthorIds = new HashSet<>();

    public static FeedbackSets empty() {
        return FeedbackSets.builder()
                .dislikedPostIds(Collections.emptySet())
                .dislikedCategoryIds(Collections.emptySet())
                .dislikedTagIds(Collections.emptySet())
                .dislikedAuthorIds(Collections.emptySet())
                .build();
    }
}
