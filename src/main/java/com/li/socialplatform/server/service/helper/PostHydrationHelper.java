package com.li.socialplatform.server.service.helper;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.common.utils.DataCacheUtil;
import com.li.socialplatform.common.utils.ViewCountCacheUtil;
import com.li.socialplatform.pojo.entity.Category;
import com.li.socialplatform.pojo.entity.Post;
import com.li.socialplatform.pojo.entity.StarRecord;
import com.li.socialplatform.pojo.entity.User;
import com.li.socialplatform.pojo.vo.PostVO;
import com.li.socialplatform.pojo.vo.TagVO;
import com.li.socialplatform.server.mapper.CategoryMapper;
import com.li.socialplatform.server.mapper.StarRecordMapper;
import com.li.socialplatform.server.mapper.UserMapper;
import com.li.socialplatform.server.service.ITagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 帖子列表视图统一组装器：
 * 批量读取作者、分类、标签、点赞状态与计数、收藏状态及 Redis 浏览增量，避免 N+1 查询与各入口字段缺失。
 */
@Component
@RequiredArgsConstructor
public class PostHydrationHelper {

    private final UserMapper userMapper;
    private final CategoryMapper categoryMapper;
    private final StarRecordMapper starRecordMapper;
    private final DataCacheUtil dataCacheUtil;
    private final ViewCountCacheUtil viewCountCacheUtil;
    private final ITagService tagService;

    /**
     * 批量组装 PostVO 列表（保持传入 posts 顺序）
     *
     * @param posts               待组装帖子列表
     * @param viewerId            当前访问用户 ID（可为 null）
     * @param filterDisabledUser  是否过滤掉已封禁作者的帖子（首页/推荐/分类/搜索应传 true）
     * @param reasonMap           可选推荐理由映射（postId -> reason）
     */
    public List<PostVO> hydrate(
            List<Post> posts,
            Long viewerId,
            boolean filterDisabledUser,
            Map<Long, String> reasonMap) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }

        List<Long> postIds = posts.stream().map(Post::getId).toList();
        Set<Long> authorIds = posts.stream()
                .map(Post::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Integer> categoryIds = posts.stream()
                .map(Post::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, User> userMap = new HashMap<>();
        if (!authorIds.isEmpty()) {
            userMapper.selectBatchIds(authorIds).forEach(u -> userMap.put(u.getId(), u));
        }

        Map<Integer, Category> categoryMap = new HashMap<>();
        if (!categoryIds.isEmpty()) {
            categoryMapper.selectBatchIds(categoryIds).forEach(c -> categoryMap.put(c.getId(), c));
        }

        Map<Long, List<TagVO>> tagMap = tagService.getTagsByPostIds(postIds);
        Map<Long, Integer> likeCounts = dataCacheUtil.getLikeCounts(postIds);
        Map<Long, Boolean> likedMap = dataCacheUtil.isLikedBatch(viewerId, postIds);
        Map<Long, Boolean> starredMap = getStarredMap(viewerId, postIds);

        Set<String> viewKeys = postIds.stream()
                .map(id -> KeyConstant.POST_VIEW_COUNT + id)
                .collect(Collectors.toSet());
        Map<Long, Long> viewDeltas = viewCountCacheUtil.batchGetDeltas(viewKeys);

        List<PostVO> result = new ArrayList<>(posts.size());
        for (Post post : posts) {
            User author = userMap.get(post.getUserId());
            if (filterDisabledUser && (author == null || !Boolean.TRUE.equals(author.getEnabled()))) {
                continue;
            }
            PostVO vo = BeanUtil.copyProperties(post, PostVO.class);
            if (author != null) {
                vo.setUsername(author.getUsername());
                vo.setNickname(author.getNickname());
            }
            Category category = categoryMap.get(post.getCategoryId());
            vo.setCategoryId(post.getCategoryId());
            vo.setCategoryName(category != null ? category.getName() : "其他");
            vo.setTags(tagMap.getOrDefault(post.getId(), List.of()));
            vo.setLiked(likedMap.getOrDefault(post.getId(), false));
            vo.setLikeCount(likeCounts.getOrDefault(post.getId(), post.getLikeCount() == null ? 0 : post.getLikeCount()));
            vo.setStarCount(post.getStarCount() == null ? 0 : post.getStarCount());
            vo.setStarred(starredMap.getOrDefault(post.getId(), false));
            vo.setCommentCount(post.getCommentCount() == null ? 0 : post.getCommentCount());
            int baseView = post.getViewCount() == null ? 0 : post.getViewCount();
            vo.setViewCount((int) (baseView + viewDeltas.getOrDefault(post.getId(), 0L)));
            if (reasonMap != null) {
                vo.setRecommendReason(reasonMap.get(post.getId()));
            }
            result.add(vo);
        }
        return result;
    }

    private Map<Long, Boolean> getStarredMap(Long viewerId, Collection<Long> postIds) {
        if (viewerId == null || postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        List<StarRecord> records = starRecordMapper.selectList(
                new LambdaQueryWrapper<StarRecord>()
                        .eq(StarRecord::getUserId, viewerId)
                        .in(StarRecord::getPostId, postIds));
        Map<Long, Boolean> map = new HashMap<>();
        records.forEach(r -> map.put(r.getPostId(), true));
        return map;
    }
}
