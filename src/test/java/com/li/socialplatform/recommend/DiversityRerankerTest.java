package com.li.socialplatform.recommend;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiversityRerankerTest {

    private final DiversityReranker reranker = new DiversityReranker();

    private ScoredPost scored(long id, long authorId, int categoryId, double score, boolean seen, boolean exploration) {
        CandidatePost post = CandidatePost.builder()
                .id(id)
                .userId(authorId)
                .categoryId(categoryId)
                .createTime(LocalDateTime.now())
                .enabled(true)
                .isPrivate(false)
                .authorEnabled(true)
                .build();
        return ScoredPost.builder()
                .post(post)
                .score(score)
                .seen(seen)
                .exploration(exploration)
                .reason("猜你喜欢")
                .build();
    }

    @Test
    void enforcesAuthorAndCategoryCapsAndReservesExplorationSlots() {
        List<ScoredPost> input = new ArrayList<>();
        // 前 8 条全是同一作者同一分类的超高分内容，后面是其他作者和探索内容
        for (int i = 1; i <= 8; i++) {
            input.add(scored(i, 100L, 3, 1.0 - i * 0.01, false, false));
        }
        for (int i = 9; i <= 14; i++) {
            input.add(scored(i, 200L + i, 4, 0.80 - i * 0.01, false, false));
        }
        for (int i = 15; i <= 18; i++) {
            input.add(scored(i, 300L + i, 5, 0.72 - i * 0.01, false, false));
        }
        // 两条可探索异分类内容（即使分高于部分常规候选，也应尽量留给探索槽位）
        input.add(scored(50L, 501L, 8, 0.55, false, true));
        input.add(scored(51L, 502L, 9, 0.54, false, true));

        List<ScoredPost> out = reranker.rerank(input, 12);
        assertEquals(input.size(), out.size());

        List<ScoredPost> firstPage = out.subList(0, 12);
        long author100InPage1 = firstPage.stream().filter(s -> s.getPost().getUserId().equals(100L)).count();
        assertTrue(author100InPage1 <= DiversityReranker.MAX_AUTHOR_PER_WINDOW);

        // 第 5 个槽位（索引 4）和第 11 个槽位（索引 10）应优先分配探索内容
        assertTrue(firstPage.get(4).isExploration());
        assertEquals("拓展发现", firstPage.get(4).getReason());
        assertTrue(firstPage.get(10).isExploration());
    }

    @Test
    void avoidsConsecutiveSameAuthorAndRelaxesWhenSingleAuthorPool() {
        List<ScoredPost> singleAuthorPool = List.of(
                scored(1L, 1L, 2, 0.9, false, false),
                scored(2L, 1L, 2, 0.8, false, false),
                scored(3L, 1L, 2, 0.7, false, false)
        );
        List<ScoredPost> out = reranker.rerank(singleAuthorPool, 12);
        assertEquals(3, out.size());
        assertEquals(List.of(1L, 2L, 3L), out.stream().map(s -> s.getPost().getId()).toList());
    }

    @Test
    void unseenBucketPrecedesSeenBucket() {
        List<ScoredPost> mixed = List.of(
                scored(1L, 10L, 2, 0.95, true, false),
                scored(2L, 11L, 2, 0.50, false, false),
                scored(3L, 12L, 4, 0.45, false, false)
        );
        List<ScoredPost> out = reranker.rerank(mixed, 12);
        assertFalse(out.get(0).isSeen());
        assertFalse(out.get(1).isSeen());
        assertTrue(out.get(2).isSeen());
    }
}
