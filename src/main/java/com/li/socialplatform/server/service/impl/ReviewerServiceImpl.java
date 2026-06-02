package com.li.socialplatform.server.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.common.constant.MessageConstant;
import com.li.socialplatform.common.utils.BanCacheUtil;
import com.li.socialplatform.common.utils.DataCacheUtil;
import com.li.socialplatform.common.utils.UserIdUtil;
import com.li.socialplatform.server.mapper.CommentMapper;
import com.li.socialplatform.server.mapper.PostMapper;
import com.li.socialplatform.pojo.entity.Post;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.pojo.vo.PostVO;
import com.li.socialplatform.server.repository.PostElasticsearchRepository;
import com.li.socialplatform.server.service.IReviewerService;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author e69d8e
 * @since 2025/12/10 14:01
 */
@Service
@RequiredArgsConstructor
public class ReviewerServiceImpl implements IReviewerService {

    private final PostMapper postMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserIdUtil userIdUtil;
    private final CommentMapper commentMapper;
    private final DataCacheUtil dataCacheUtil;
    private final BanCacheUtil banCacheUtil;
    private final PostElasticsearchRepository postElasticsearchRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    public Result banPost(Long id) {
        Post post = postMapper.selectById(id);
        if (post == null) {
            return Result.error(MessageConstant.POST_NOT_EXIST);
        }
        Long reviewerId = userIdUtil.getUserId();
        post.setEnabled(!post.getEnabled());
        if (!post.getEnabled()) {
            banCacheUtil.addBanPost(reviewerId, id);
        } else {
            banCacheUtil.removeBanPost(reviewerId, id);
        }
        postMapper.updateById(post);
        // 同步到Elasticsearch
        postElasticsearchRepository.save(post);
        return Result.ok(MessageConstant.BAN_SUCCESS, "");
    }

    @Override
    public Result listBanPost(Integer pageNum, Integer pageSize) {
        long start = ((long) (pageNum - 1) * pageSize);
        long end = start + pageSize - 1;
        Long userId = userIdUtil.getUserId();
        Long total = banCacheUtil.getBanPostTotal(userId);
        if (total == null || total == 0) {
            return Result.ok(List.of(), 0L);
        }
        if (start > total) {
            return Result.ok(List.of(), 0L);
        }
        if (end > total) {
            end = total - 1;
        }
        Set<Object> members = banCacheUtil.getBanPostIds(userId, start, end);
        if (members == null || members.isEmpty()) {
            return Result.ok(List.of(), 0L);
        }
        List<Long> ids = members.stream().map(member -> Long.valueOf(member.toString())).toList();
        List<PostVO> postVOS = new ArrayList<>();
        for (Long id : ids) {
            Post post = postMapper.selectById(id);
            PostVO postVO = BeanUtil.copyProperties(post, PostVO.class);
            postVO.setLikeCount(dataCacheUtil.getLikeCount(id));
            if (userId != null) {
                postVO.setLiked(dataCacheUtil.isLiked(id, userId));
            } else {
                postVO.setLiked(false);
            }
            postVO.setEnabled(false);
            postVOS.add(postVO);
        }
        return Result.ok(postVOS, total);
    }

    @Override
    public Result deleteComment(Long id, Long postId) {
        redisTemplate.opsForZSet().remove(KeyConstant.COMMENT_KEY + postId, id);
        commentMapper.deleteById(id);
        return Result.ok(MessageConstant.DELETE_SUCCESS, "");
    }

    @Override
    public Result searchBanPost(String keyword, Integer pageNum, Integer pageSize) {
        Long reviewerId = userIdUtil.getUserId();
        // 从 Redis 获取当前审核员封禁的帖子 ID
        Long banTotal = banCacheUtil.getBanPostTotal(reviewerId);
        if (banTotal == null || banTotal == 0) {
            return Result.ok(List.of(), 0L);
        }
        Set<Object> allBanMembers = banCacheUtil.getBanPostIds(reviewerId, 0, banTotal - 1);
        if (allBanMembers == null || allBanMembers.isEmpty()) {
            return Result.ok(List.of(), 0L);
        }
        List<Long> banIds = allBanMembers.stream()
                .map(member -> Long.valueOf(member.toString()))
                .toList();

        // 构建 ES 查询：关键词匹配 title/content + 过滤封禁帖子 ID
        List<String> idStrings = banIds.stream().map(String::valueOf).toList();
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b
                        .must(m -> m.multiMatch(mm -> mm.fields("title", "content").query(keyword)))
                        .filter(f -> f.terms(t -> t.field("id").terms(tv -> tv.value(idStrings.stream().map(FieldValue::of).toList()))))))
                .withPageable(PageRequest.of(pageNum - 1, pageSize))
                .withSort(Sort.by(Sort.Direction.DESC, "createTime"))
                .build();

        SearchHits<Post> hits = elasticsearchOperations.search(query, Post.class);
        long total = hits.getTotalHits();
        List<PostVO> postVOS = new ArrayList<>();
        for (SearchHit<Post> hit : hits) {
            Post post = hit.getContent();
            PostVO postVO = BeanUtil.copyProperties(post, PostVO.class);
            postVO.setLikeCount(dataCacheUtil.getLikeCount(post.getId()));
            postVO.setLiked(reviewerId != null && dataCacheUtil.isLiked(post.getId(), reviewerId));
            postVO.setEnabled(false);
            postVOS.add(postVO);
        }
        return Result.ok(postVOS, total);
    }
}
