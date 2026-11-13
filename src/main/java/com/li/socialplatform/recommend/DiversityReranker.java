package com.li.socialplatform.recommend;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 多样性重排器（MMR / 滑动窗口约束）：
 * <ul>
 *   <li>优先消费未曝光（unseen）内容，已曝光内容仅在未曝光不足时兜底补位</li>
 *   <li>每个分页窗口（默认 12 条）限制单作者上限（<=2）和单分类上限（<=4），并尽量避免同作者相邻连刷</li>
 *   <li>每页预留约 15%~20% 探索槽位，防止陷入信息茧房</li>
 * </ul>
 */
@Component
public class DiversityReranker {

    public static final int DEFAULT_WINDOW_SIZE = 12;
    public static final int MAX_AUTHOR_PER_WINDOW = 2;
    public static final int MAX_CATEGORY_PER_WINDOW = 4;

    public List<ScoredPost> rerank(List<ScoredPost> sortedCandidates, int windowSize) {
        if (sortedCandidates == null || sortedCandidates.isEmpty()) {
            return List.of();
        }
        int safeWindow = windowSize <= 0 ? DEFAULT_WINDOW_SIZE : windowSize;

        List<ScoredPost> unseen = new ArrayList<>();
        List<ScoredPost> seen = new ArrayList<>();
        for (ScoredPost sp : sortedCandidates) {
            if (sp.isSeen()) {
                seen.add(sp);
            } else {
                unseen.add(sp);
            }
        }

        List<ScoredPost> reranked = new ArrayList<>(sortedCandidates.size());
        rerankBucket(unseen, safeWindow, reranked);
        rerankBucket(seen, safeWindow, reranked);
        return reranked;
    }

    private void rerankBucket(List<ScoredPost> bucket, int windowSize, List<ScoredPost> output) {
        if (bucket.isEmpty()) {
            return;
        }
        LinkedList<ScoredPost> remaining = new LinkedList<>(bucket);
        while (!remaining.isEmpty()) {
            Map<Long, Integer> authorCounts = new HashMap<>();
            Map<Integer, Integer> categoryCounts = new HashMap<>();
            Long lastAuthorId = output.isEmpty() ? null : output.get(output.size() - 1).getPost().getUserId();

            int filledInWindow = 0;
            while (filledInWindow < windowSize && !remaining.isEmpty()) {
                boolean isExploreSlot = filledInWindow == 4 || filledInWindow == 10;
                ScoredPost chosen = pickNext(remaining, authorCounts, categoryCounts, lastAuthorId, isExploreSlot);
                if (chosen == null) {
                    // 若严格多维上限在当前小池中无法满足，放宽上限按原分顺序补全
                    chosen = remaining.pollFirst();
                } else {
                    remaining.remove(chosen);
                }
                if (isExploreSlot && chosen.isExploration()) {
                    chosen.setReason("拓展发现");
                }
                output.add(chosen);
                Long authorId = chosen.getPost().getUserId();
                Integer categoryId = chosen.getPost().getCategoryId();
                if (authorId != null) {
                    authorCounts.merge(authorId, 1, Integer::sum);
                    lastAuthorId = authorId;
                }
                if (categoryId != null) {
                    categoryCounts.merge(categoryId, 1, Integer::sum);
                }
                filledInWindow++;
            }
        }
    }

    private ScoredPost pickNext(
            List<ScoredPost> remaining,
            Map<Long, Integer> authorCounts,
            Map<Integer, Integer> categoryCounts,
            Long lastAuthorId,
            boolean preferExploration) {
        if (preferExploration) {
            ScoredPost explore = findFirstMatching(remaining, authorCounts, categoryCounts, lastAuthorId, Boolean.TRUE);
            if (explore != null) {
                return explore;
            }
        }
        // 非探索槽位优先挑选常规内容，尽量将稀缺的探索候选留给探索槽位
        ScoredPost regularStrict = findFirstMatching(remaining, authorCounts, categoryCounts, lastAuthorId, Boolean.FALSE);
        if (regularStrict != null) {
            return regularStrict;
        }
        ScoredPost anyStrict = findFirstMatching(remaining, authorCounts, categoryCounts, lastAuthorId, null);
        if (anyStrict != null) {
            return anyStrict;
        }
        // 次优回退：允许分类达到上限，但仍尽量避免同作者连续出现
        for (ScoredPost candidate : remaining) {
            Long authorId = candidate.getPost().getUserId();
            if (authorId == null || !authorId.equals(lastAuthorId)) {
                return candidate;
            }
        }
        return null;
    }

    private ScoredPost findFirstMatching(
            List<ScoredPost> remaining,
            Map<Long, Integer> authorCounts,
            Map<Integer, Integer> categoryCounts,
            Long lastAuthorId,
            Boolean explorationFilter) {
        for (ScoredPost candidate : remaining) {
            if (explorationFilter != null && candidate.isExploration() != explorationFilter) {
                continue;
            }
            Long authorId = candidate.getPost().getUserId();
            Integer categoryId = candidate.getPost().getCategoryId();
            if (authorId != null) {
                if (authorId.equals(lastAuthorId)) {
                    continue;
                }
                if (authorCounts.getOrDefault(authorId, 0) >= MAX_AUTHOR_PER_WINDOW) {
                    continue;
                }
            }
            if (categoryId != null && categoryCounts.getOrDefault(categoryId, 0) >= MAX_CATEGORY_PER_WINDOW) {
                continue;
            }
            return candidate;
        }
        return null;
    }
}
