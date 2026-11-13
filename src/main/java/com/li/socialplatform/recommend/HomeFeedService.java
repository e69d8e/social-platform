package com.li.socialplatform.recommend;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.socialplatform.common.constant.CacheTtl;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.common.properties.SystemConstants;
import com.li.socialplatform.common.utils.*;
import com.li.socialplatform.pojo.entity.*;
import com.li.socialplatform.pojo.vo.PostVO;
import com.li.socialplatform.pojo.vo.TagVO;
import com.li.socialplatform.server.mapper.*;
import com.li.socialplatform.server.service.IRecommendFeedbackService;
import com.li.socialplatform.server.service.ITagService;
import com.li.socialplatform.server.service.helper.PostHydrationHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 首页信息流统一调度服务：
 * <ul>
 *   <li>recommend：多路召回（分类/标签兴趣、关注作者、全站热门、最新探索）-> 多因子精排 -> 多样性重排 -> 快照分页 + 已曝光抑制</li>
 *   <li>hot：互动热度 + 天尺度重力时间衰减榜</li>
 *   <li>latest：Redis ZSet 实时时间流（修复过滤后空页误判结束与末页误重载全表的问题）</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HomeFeedService {

    private static final int RECALL_TARGET_SIZE = 180;
    private static final int TOP_INTEREST_CATEGORIES = 5;
    private static final int TOP_INTEREST_TAGS = 8;

    private final UserIdUtil userIdUtil;
    private final SystemConstants systemConstants;
    private final RedisUtil redisUtil;
    private final BlacklistUtil blacklistUtil;
    private final UserIntersetScoreUtil userIntersetScoreUtil;
    private final ViewCountCacheUtil viewCountCacheUtil;
    private final IRecommendFeedbackService recommendFeedbackService;
    private final ITagService tagService;
    private final FollowMapper followMapper;
    private final PostMapper postMapper;
    private final HomePostMapper homePostMapper;
    private final UserMapper userMapper;
    private final CategoryMapper categoryMapper;
    private final ElasticsearchOperations elasticsearchOperations;
    private final RecommendScorer recommendScorer;
    private final DiversityReranker diversityReranker;
    private final PostHydrationHelper postHydrationHelper;

    public Result loadHomeFeed(
            Long lastId,
            Integer offset,
            String modeRaw,
            Boolean refresh,
            Long seed,
            String visitorId) {
        FeedMode mode = FeedMode.from(modeRaw);
        Long userId = userIdUtil.getUserId();
        int pageSize = Integer.parseInt(systemConstants.defaultPageSize);
        int safeOffset = offset == null || offset < 0 ? 0 : offset;

        UserRecommendProfile profile = buildUserProfile(userId, visitorId);
        if (mode == FeedMode.LATEST) {
            return Result.ok(loadLatestFeed(lastId, safeOffset, pageSize, userId, profile));
        }

        boolean isRefresh = Boolean.TRUE.equals(refresh);
        long activeSeed = resolveSeed(seed, lastId, isRefresh, safeOffset);
        String viewerScope = resolveViewerScope(userId, visitorId);
        String snapshotKey = buildSnapshotKey(viewerScope, mode, activeSeed);

        List<RankedPostEntry> rankedList = null;
        if (!isRefresh && safeOffset > 0) {
            rankedList = readSnapshot(snapshotKey);
        }
        if (rankedList == null) {
            rankedList = buildAndCacheSnapshot(profile, mode, activeSeed, pageSize, snapshotKey, viewerScope, isRefresh);
        }

        return Result.ok(sliceAndHydrateSnapshot(rankedList, safeOffset, pageSize, userId, profile, mode, activeSeed, viewerScope));
    }

    private ScrollResult<PostVO> loadLatestFeed(
            Long lastId,
            int initialOffset,
            int pageSize,
            Long viewerId,
            UserRecommendProfile profile) {
        long cursorMaxScore = lastId == null || lastId <= 0 ? System.currentTimeMillis() : lastId;
        int cursorOffset = Math.max(0, initialOffset);

        ensureLatestZSetInitialized();
        String homePostKey = KeyConstant.HOME_POST_LIST_KEY;
        Long totalInZSet = redisUtil.zsets().zCard(homePostKey);
        if (totalInZSet == null || totalInZSet == 0L) {
            return emptyScrollResult("latest", cursorMaxScore, 0);
        }

        List<Post> collected = new ArrayList<>(pageSize);
        long minTime = cursorMaxScore;
        int nextOffset = cursorOffset;
        boolean reachedEnd = false;

        int maxRounds = 6;
        int fetchBatchSize = Math.max(pageSize * 2, 24);
        for (int round = 0; round < maxRounds && collected.size() < pageSize && !reachedEnd; round++) {
            Set<ZSetOperations.TypedTuple<Object>> tuples = redisUtil.zsets()
                    .reverseRangeByScoreWithScores(homePostKey, 0, cursorMaxScore, cursorOffset, fetchBatchSize);
            if (tuples == null || tuples.isEmpty()) {
                reachedEnd = true;
                break;
            }
            if (tuples.size() < fetchBatchSize) {
                reachedEnd = true;
            }

            List<Long> batchIds = new ArrayList<>(tuples.size());
            Map<Long, Long> scoreByPostId = new LinkedHashMap<>();
            for (ZSetOperations.TypedTuple<Object> tuple : tuples) {
                if (tuple.getValue() == null || tuple.getScore() == null) {
                    continue;
                }
                Long pid = Long.valueOf(tuple.getValue().toString());
                batchIds.add(pid);
                scoreByPostId.put(pid, tuple.getScore().longValue());
            }

            Map<Long, Post> postMap = batchIds.isEmpty() ? Map.of()
                    : postMapper.selectBatchIds(batchIds).stream().collect(Collectors.toMap(Post::getId, p -> p));
            Map<Long, User> authorMap = loadAuthorMap(postMap.values());

            int sameScoreRun = 0;
            int processedInRound = 0;
            long lastScoreInRound = cursorMaxScore;
            boolean pageFilledMidBatch = false;

            for (Long pid : batchIds) {
                processedInRound++;
                long score = scoreByPostId.get(pid);
                if (score == lastScoreInRound) {
                    sameScoreRun++;
                } else {
                    lastScoreInRound = score;
                    sameScoreRun = 1;
                }

                Post post = postMap.get(pid);
                if (post == null || !Boolean.TRUE.equals(post.getEnabled()) || Boolean.TRUE.equals(post.getIsPrivate())) {
                    // 从首页时间流移除失效/私密帖，保持后续干净
                    redisUtil.zsets().remove(homePostKey, pid);
                    continue;
                }
                User author = authorMap.get(post.getUserId());
                if (author == null || !Boolean.TRUE.equals(author.getEnabled())) {
                    continue;
                }
                if (isBlockedOrDisliked(post.getUserId(), post.getId(), profile)) {
                    continue;
                }

                collected.add(post);
                minTime = score;
                nextOffset = (score == cursorMaxScore) ? cursorOffset + sameScoreRun : sameScoreRun;
                if (collected.size() >= pageSize) {
                    pageFilledMidBatch = true;
                    break;
                }
            }

            if (pageFilledMidBatch) {
                if (processedInRound < tuples.size()) {
                    reachedEnd = false;
                }
                break;
            }

            // 本批扫描完仍不足一页：推进游标继续探测
            long prevCursorMaxScore = cursorMaxScore;
            cursorMaxScore = lastScoreInRound;
            cursorOffset = (lastScoreInRound == prevCursorMaxScore)
                    ? cursorOffset + sameScoreRun
                    : sameScoreRun;
            minTime = cursorMaxScore;
            nextOffset = cursorOffset;
        }

        List<PostVO> vos = postHydrationHelper.hydrate(collected, viewerId, true, null);
        ScrollResult<PostVO> result = new ScrollResult<>();
        result.setList(vos);
        result.setMinTime(minTime);
        result.setOffset(nextOffset);
        result.setHasMore(!reachedEnd);
        result.setMode("latest");
        return result;
    }

    private void ensureLatestZSetInitialized() {
        Long currentSize = redisUtil.zsets().zCard(KeyConstant.HOME_POST_LIST_KEY);
        if (currentSize != null && currentSize > 0L) {
            return;
        }
        List<HomePost> homePosts = homePostMapper.selectList(null);
        if (!homePosts.isEmpty()) {
            List<Long> postIds = homePosts.stream().map(HomePost::getPostId).distinct().toList();
            Map<Long, Post> validPosts = postMapper.selectBatchIds(postIds).stream()
                    .filter(p -> Boolean.TRUE.equals(p.getEnabled()) && !Boolean.TRUE.equals(p.getIsPrivate()))
                    .collect(Collectors.toMap(Post::getId, p -> p));
            Map<Object, Double> memberScores = new HashMap<>();
            for (HomePost hp : homePosts) {
                Post p = validPosts.get(hp.getPostId());
                if (p != null) {
                    memberScores.put(p.getId(), toEpochMilli(p.getCreateTime()));
                }
            }
            if (!memberScores.isEmpty()) {
                redisUtil.zAddAll(KeyConstant.HOME_POST_LIST_KEY, memberScores);
                redisUtil.expire(KeyConstant.HOME_POST_LIST_KEY, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
                return;
            }
        }

        List<Post> publicPosts = postMapper.selectList(new LambdaQueryWrapper<Post>()
                .eq(Post::getEnabled, true)
                .and(w -> w.eq(Post::getIsPrivate, false).or().isNull(Post::getIsPrivate))
                .orderByDesc(Post::getCreateTime)
                .last("LIMIT 300"));
        if (!publicPosts.isEmpty()) {
            Map<Object, Double> memberScores = new HashMap<>();
            publicPosts.forEach(p -> memberScores.put(p.getId(), toEpochMilli(p.getCreateTime())));
            redisUtil.zAddAll(KeyConstant.HOME_POST_LIST_KEY, memberScores);
            redisUtil.expire(KeyConstant.HOME_POST_LIST_KEY, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
        }
    }

    private List<RankedPostEntry> buildAndCacheSnapshot(
            UserRecommendProfile profile,
            FeedMode mode,
            long seed,
            int pageSize,
            String snapshotKey,
            String viewerScope,
            boolean isRefresh) {
        List<CandidatePost> candidates = recallCandidates(profile, mode);
        if (candidates.isEmpty()) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now();
        List<ScoredPost> scored = recommendScorer.scoreAll(candidates, profile, mode, now, seed);

        // 换一换时若未曝光内容已耗尽（用户把当前库都刷过一遍），重置曝光窗重新洗牌，避免卡死旧顺序
        if (isRefresh && !scored.isEmpty() && scored.stream().allMatch(ScoredPost::isSeen)) {
            clearSeenWindow(viewerScope);
            profile.setSeenPostIds(Set.of());
            scored = recommendScorer.scoreAll(candidates, profile, mode, now, seed);
        }

        List<ScoredPost> finalOrdered = (mode == FeedMode.RECOMMEND)
                ? diversityReranker.rerank(scored, pageSize)
                : scored;

        List<RankedPostEntry> entries = finalOrdered.stream()
                .map(sp -> new RankedPostEntry(sp.getPost().getId(), sp.getReason()))
                .toList();

        if (!entries.isEmpty() && !"anon".equals(viewerScope)) {
            redisUtil.strings().opsForValue().set(
                    snapshotKey,
                    JSON.toJSONString(entries),
                    CacheTtl.RECOMMEND_SNAPSHOT_MINUTES,
                    TimeUnit.MINUTES);
        }
        return entries;
    }

    private ScrollResult<PostVO> sliceAndHydrateSnapshot(
            List<RankedPostEntry> rankedList,
            int startOffset,
            int pageSize,
            Long viewerId,
            UserRecommendProfile profile,
            FeedMode mode,
            long activeSeed,
            String viewerScope) {
        if (rankedList == null || rankedList.isEmpty() || startOffset >= rankedList.size()) {
            return emptyScrollResult(mode.getCode(), activeSeed, rankedList == null ? 0 : rankedList.size());
        }

        // 先取一段略大于 pageSize 的切片做实时有效性复核（防止期间帖子被删/私密/拉黑）
        List<Post> selectedPosts = new ArrayList<>(pageSize);
        Map<Long, String> reasonMap = new HashMap<>();
        int cursor = startOffset;

        while (cursor < rankedList.size() && selectedPosts.size() < pageSize) {
            int batchEnd = Math.min(rankedList.size(), cursor + pageSize * 2);
            List<RankedPostEntry> window = rankedList.subList(cursor, batchEnd);
            List<Long> windowIds = window.stream().map(RankedPostEntry::getPostId).toList();
            Map<Long, Post> postMap = windowIds.isEmpty() ? Map.of()
                    : postMapper.selectBatchIds(windowIds).stream().collect(Collectors.toMap(Post::getId, p -> p));
            Map<Long, User> authorMap = loadAuthorMap(postMap.values());

            for (RankedPostEntry entry : window) {
                cursor++;
                Post post = postMap.get(entry.getPostId());
                if (post == null || !Boolean.TRUE.equals(post.getEnabled()) || Boolean.TRUE.equals(post.getIsPrivate())) {
                    continue;
                }
                User author = authorMap.get(post.getUserId());
                if (author == null || !Boolean.TRUE.equals(author.getEnabled())) {
                    continue;
                }
                if (isBlockedOrDisliked(post.getUserId(), post.getId(), profile)) {
                    continue;
                }
                selectedPosts.add(post);
                if (StringUtils.isNotBlank(entry.getReason())) {
                    reasonMap.put(post.getId(), entry.getReason());
                }
                if (selectedPosts.size() >= pageSize) {
                    break;
                }
            }
        }

        List<PostVO> vos = postHydrationHelper.hydrate(selectedPosts, viewerId, true, reasonMap);
        if (!vos.isEmpty() && !"anon".equals(viewerScope)) {
            markPostsSeen(viewerScope, vos.stream().map(PostVO::getId).toList());
        }

        boolean hasMore = cursor < rankedList.size();
        ScrollResult<PostVO> result = new ScrollResult<>();
        result.setList(vos);
        result.setMinTime(activeSeed);
        result.setOffset(cursor);
        result.setHasMore(hasMore);
        result.setMode(mode.getCode());
        result.setSeed(activeSeed);
        return result;
    }

    /**
     * 多路召回：ES 为主路，任一路异常或召回不足时平滑回退 MySQL 兜底
     */
    private List<CandidatePost> recallCandidates(UserRecommendProfile profile, FeedMode mode) {
        Map<Long, Post> mergedPosts = new LinkedHashMap<>();

        if (mode == FeedMode.RECOMMEND) {
            // 通路 1：兴趣分类与兴趣标签召回
            recallByInterestsFromEs(profile, mergedPosts, 80);
            // 通路 2：关注作者最新动态召回
            recallByFollowedAuthors(profile.getFollowedUserIds(), mergedPosts, 35);
        }
        // 通路 3：全站高质量热门池
        recallHotPool(mergedPosts, 70);
        // 通路 4：全站最新与探索池（保障冷启动及长尾新内容触达）
        recallFreshPool(mergedPosts, 60);

        if (mergedPosts.isEmpty()) {
            return List.of();
        }

        // 用 MySQL 主库权威字段刷新，消除 ES 中 starCount / commentCount / enabled 可能滞后的问题
        List<Long> candidateIds = mergedPosts.keySet().stream().limit(RECALL_TARGET_SIZE).toList();
        List<Post> canonicalPosts = postMapper.selectBatchIds(candidateIds);
        if (canonicalPosts.isEmpty()) {
            return List.of();
        }

        Map<Long, User> authorMap = loadAuthorMap(canonicalPosts);
        Map<Integer, String> categoryNameMap = loadCategoryNameMap(canonicalPosts);
        Map<Long, List<TagVO>> tagMap = tagService.getTagsByPostIds(candidateIds);

        Set<String> viewKeys = candidateIds.stream()
                .map(id -> KeyConstant.POST_VIEW_COUNT + id)
                .collect(Collectors.toSet());
        Map<Long, Long> viewDeltas = viewCountCacheUtil.batchGetDeltas(viewKeys);

        List<CandidatePost> candidates = new ArrayList<>(canonicalPosts.size());
        for (Post post : canonicalPosts) {
            User author = authorMap.get(post.getUserId());
            boolean authorEnabled = author != null && Boolean.TRUE.equals(author.getEnabled());
            List<TagVO> postTags = tagMap.getOrDefault(post.getId(), List.of());
            List<Long> tagIds = postTags.stream().map(TagVO::getId).toList();
            Map<Long, String> tagNamesById = postTags.stream()
                    .collect(Collectors.toMap(TagVO::getId, TagVO::getName, (a, b) -> a));

            int baseView = post.getViewCount() == null ? 0 : post.getViewCount();
            int totalView = (int) (baseView + viewDeltas.getOrDefault(post.getId(), 0L));

            candidates.add(CandidatePost.builder()
                    .id(post.getId())
                    .userId(post.getUserId())
                    .categoryId(post.getCategoryId())
                    .categoryName(categoryNameMap.getOrDefault(post.getCategoryId(), "其他"))
                    .tagIds(tagIds)
                    .tagNamesById(tagNamesById)
                    .likeCount(post.getLikeCount())
                    .starCount(post.getStarCount())
                    .commentCount(post.getCommentCount())
                    .viewCount(totalView)
                    .createTime(post.getCreateTime())
                    .enabled(post.getEnabled())
                    .isPrivate(post.getIsPrivate())
                    .authorEnabled(authorEnabled)
                    .build());
        }
        return candidates;
    }

    private void recallByInterestsFromEs(UserRecommendProfile profile, Map<Long, Post> mergedPosts, int limit) {
        if (!profile.hasPersonalSignals()) {
            return;
        }
        List<Integer> topCatIds = profile.getDecayedCategoryScores().entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .limit(TOP_INTEREST_CATEGORIES)
                .map(Map.Entry::getKey)
                .toList();
        List<Long> topTagIds = profile.getDecayedTagScores().entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(TOP_INTEREST_TAGS)
                .map(Map.Entry::getKey)
                .toList();
        if (topCatIds.isEmpty() && topTagIds.isEmpty()) {
            return;
        }

        try {
            NativeQuery query = NativeQuery.builder()
                    .withQuery(q -> q.bool(b -> {
                        applyCommonEsPublicFilter(b, profile.getHiddenUserIds());
                        for (Integer catId : topCatIds) {
                            b.should(s -> s.term(t -> t.field("categoryId").value(catId).boost(2.0f)));
                        }
                        for (Long tagId : topTagIds) {
                            b.should(s -> s.term(t -> t.field("tagIds").value(FieldValue.of(String.valueOf(tagId))).boost(3.0f)));
                        }
                        b.minimumShouldMatch("1");
                        return b;
                    }))
                    .withPageable(PageRequest.of(0, limit))
                    .withSort(Sort.by(Sort.Direction.DESC, "_score", "likeCount"))
                    .build();
            elasticsearchOperations.search(query, Post.class).stream()
                    .map(SearchHit::getContent)
                    .forEach(p -> mergedPosts.putIfAbsent(p.getId(), p));
        } catch (Exception e) {
            log.warn("ES 兴趣路召回失败，改用 MySQL 兜底: {}", e.getMessage());
            if (!topCatIds.isEmpty()) {
                postMapper.selectList(new LambdaQueryWrapper<Post>()
                                .eq(Post::getEnabled, true)
                                .and(w -> w.eq(Post::getIsPrivate, false).or().isNull(Post::getIsPrivate))
                                .in(Post::getCategoryId, topCatIds)
                                .orderByDesc(Post::getLikeCount)
                                .last("LIMIT " + limit))
                        .forEach(p -> mergedPosts.putIfAbsent(p.getId(), p));
            }
        }
    }

    private void recallByFollowedAuthors(Set<Long> followedUserIds, Map<Long, Post> mergedPosts, int limit) {
        if (followedUserIds == null || followedUserIds.isEmpty()) {
            return;
        }
        List<Long> subset = followedUserIds.stream().limit(50).toList();
        postMapper.selectList(new LambdaQueryWrapper<Post>()
                        .eq(Post::getEnabled, true)
                        .and(w -> w.eq(Post::getIsPrivate, false).or().isNull(Post::getIsPrivate))
                        .in(Post::getUserId, subset)
                        .orderByDesc(Post::getCreateTime)
                        .last("LIMIT " + limit))
                .forEach(p -> mergedPosts.putIfAbsent(p.getId(), p));
    }

    private void recallHotPool(Map<Long, Post> mergedPosts, int limit) {
        try {
            NativeQuery query = NativeQuery.builder()
                    .withQuery(q -> q.bool(b -> {
                        applyCommonEsPublicFilter(b, Set.of());
                        return b;
                    }))
                    .withPageable(PageRequest.of(0, limit))
                    .withSort(Sort.by(Sort.Order.desc("likeCount"), Sort.Order.desc("viewCount")))
                    .build();
            List<Post> esHits = elasticsearchOperations.search(query, Post.class).stream()
                    .map(SearchHit::getContent)
                    .toList();
            if (!esHits.isEmpty()) {
                esHits.forEach(p -> mergedPosts.putIfAbsent(p.getId(), p));
                return;
            }
        } catch (Exception e) {
            log.warn("ES 热门池召回失败，回退 MySQL: {}", e.getMessage());
        }

        postMapper.selectList(new LambdaQueryWrapper<Post>()
                        .eq(Post::getEnabled, true)
                        .and(w -> w.eq(Post::getIsPrivate, false).or().isNull(Post::getIsPrivate))
                        .orderByDesc(Post::getLikeCount)
                        .orderByDesc(Post::getViewCount)
                        .last("LIMIT " + limit))
                .forEach(p -> mergedPosts.putIfAbsent(p.getId(), p));
    }

    private void recallFreshPool(Map<Long, Post> mergedPosts, int limit) {
        postMapper.selectList(new LambdaQueryWrapper<Post>()
                        .eq(Post::getEnabled, true)
                        .and(w -> w.eq(Post::getIsPrivate, false).or().isNull(Post::getIsPrivate))
                        .orderByDesc(Post::getCreateTime)
                        .last("LIMIT " + limit))
                .forEach(p -> mergedPosts.putIfAbsent(p.getId(), p));
    }

    private void applyCommonEsPublicFilter(
            co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder b,
            Set<Long> hiddenUsers) {
        b.filter(f -> f.term(t -> t.field("enabled").value(FieldValue.of(true))));
        b.mustNot(mn -> mn.term(t -> t.field("isPrivate").value(FieldValue.of(true))));
        Query hiddenAuthors = EsQueryUtil.excludeKeywordField("userId", hiddenUsers);
        if (hiddenAuthors != null) {
            b.mustNot(hiddenAuthors);
        }
    }

    private UserRecommendProfile buildUserProfile(Long userId, String visitorId) {
        Set<Long> hidden = blacklistUtil.hiddenUserIds(userId);
        FeedbackSets feedbackSets = recommendFeedbackService.getFeedbackSets(userId);
        String viewerScope = resolveViewerScope(userId, visitorId);
        Set<Long> seenIds = loadSeenPostIds(viewerScope);

        if (userId == null) {
            return UserRecommendProfile.builder()
                    .hiddenUserIds(hidden)
                    .feedbackSets(feedbackSets)
                    .seenPostIds(seenIds)
                    .build();
        }

        Map<Integer, Double> decayedCats = userIntersetScoreUtil.getDecayedCategoryScores(userId);
        Map<Long, Double> decayedTags = userIntersetScoreUtil.getDecayedTagScores(userId);
        Set<Long> followedIds = followMapper.selectList(
                        new LambdaQueryWrapper<Follow>()
                                .select(Follow::getFolloweeId)
                                .eq(Follow::getFollowerId, userId))
                .stream()
                .map(Follow::getFolloweeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return UserRecommendProfile.builder()
                .userId(userId)
                .decayedCategoryScores(decayedCats)
                .decayedTagScores(decayedTags)
                .followedUserIds(followedIds)
                .hiddenUserIds(hidden)
                .seenPostIds(seenIds)
                .feedbackSets(feedbackSets)
                .build();
    }

    private Set<Long> loadSeenPostIds(String viewerScope) {
        if ("anon".equals(viewerScope)) {
            return Set.of();
        }
        Set<Object> members = redisUtil.sets().members(KeyConstant.RECOMMEND_SEEN_KEY + viewerScope);
        if (members == null || members.isEmpty()) {
            return Set.of();
        }
        Set<Long> result = new HashSet<>(members.size());
        for (Object m : members) {
            try {
                result.add(Long.valueOf(m.toString()));
            } catch (NumberFormatException ignored) {
                // ignore malformed entry
            }
        }
        return result;
    }

    private void markPostsSeen(String viewerScope, Collection<Long> postIds) {
        if ("anon".equals(viewerScope) || postIds == null || postIds.isEmpty()) {
            return;
        }
        String seenKey = KeyConstant.RECOMMEND_SEEN_KEY + viewerScope;
        redisUtil.sets().add(seenKey, postIds.toArray());
        redisUtil.expire(seenKey, CacheTtl.RECOMMEND_SEEN_HOURS, TimeUnit.HOURS);
    }

    private void clearSeenWindow(String viewerScope) {
        if (!"anon".equals(viewerScope)) {
            redisUtil.delete(KeyConstant.RECOMMEND_SEEN_KEY + viewerScope);
        }
    }

    private List<RankedPostEntry> readSnapshot(String snapshotKey) {
        String json = redisUtil.strings().opsForValue().get(snapshotKey);
        if (StringUtils.isBlank(json)) {
            return null;
        }
        return JSON.parseArray(json, RankedPostEntry.class);
    }

    private Map<Long, User> loadAuthorMap(Collection<Post> posts) {
        Set<Long> authorIds = posts.stream().map(Post::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (authorIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, User> map = new HashMap<>(authorIds.size());
        userMapper.selectBatchIds(authorIds).forEach(u -> map.put(u.getId(), u));
        return map;
    }

    private Map<Integer, String> loadCategoryNameMap(Collection<Post> posts) {
        Set<Integer> catIds = posts.stream().map(Post::getCategoryId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (catIds.isEmpty()) {
            return Map.of();
        }
        Map<Integer, String> map = new HashMap<>(catIds.size());
        categoryMapper.selectBatchIds(catIds).forEach(c -> map.put(c.getId(), c.getName()));
        return map;
    }

    private boolean isBlockedOrDisliked(Long authorId, Long postId, UserRecommendProfile profile) {
        if (authorId != null && profile.getHiddenUserIds().contains(authorId)) {
            return true;
        }
        FeedbackSets fb = profile.getFeedbackSets();
        if (fb == null) {
            return false;
        }
        if (fb.getDislikedPostIds().contains(postId)) {
            return true;
        }
        return authorId != null && fb.getDislikedAuthorIds().contains(authorId);
    }

    private String resolveViewerScope(Long userId, String visitorId) {
        if (userId != null) {
            return "u:" + userId;
        }
        if (StringUtils.isNotBlank(visitorId)) {
            String safeVisitor = visitorId.trim().replaceAll("[^a-zA-Z0-9_-]", "");
            if (!safeVisitor.isEmpty()) {
                return "v:" + safeVisitor.substring(0, Math.min(safeVisitor.length(), 48));
            }
        }
        return "anon";
    }

    private long resolveSeed(Long explicitSeed, Long lastId, boolean refresh, int offset) {
        if (explicitSeed != null && explicitSeed != 0L) {
            return explicitSeed;
        }
        if (!refresh && offset > 0 && lastId != null && lastId > 0L) {
            return lastId;
        }
        return System.currentTimeMillis();
    }

    private String buildSnapshotKey(String viewerScope, FeedMode mode, long seed) {
        return KeyConstant.RECOMMEND_SNAPSHOT_KEY + viewerScope + ":" + mode.getCode() + ":" + seed;
    }

    private ScrollResult<PostVO> emptyScrollResult(String modeCode, long minTime, int offset) {
        ScrollResult<PostVO> res = new ScrollResult<>();
        res.setList(List.of());
        res.setMinTime(minTime);
        res.setOffset(offset);
        res.setHasMore(false);
        res.setMode(modeCode);
        res.setSeed(minTime);
        return res;
    }

    private double toEpochMilli(LocalDateTime createTime) {
        return createTime != null
                ? createTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                : System.currentTimeMillis();
    }
}
