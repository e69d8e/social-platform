package com.li.socialplatform.server.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.socialplatform.common.constant.CacheTtl;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.common.constant.MessageConstant;
import com.li.socialplatform.common.utils.RedisUtil;
import com.li.socialplatform.pojo.entity.PostTag;
import com.li.socialplatform.pojo.entity.Tag;
import com.li.socialplatform.pojo.vo.TagVO;
import com.li.socialplatform.server.mapper.PostTagMapper;
import com.li.socialplatform.server.mapper.TagMapper;
import com.li.socialplatform.server.service.ITagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements ITagService {

    private final TagMapper tagMapper;
    private final PostTagMapper postTagMapper;
    private final RedisUtil redisUtil;

    @Override
    public List<String> normalizeTagNames(List<String> rawNames) {
        if (rawNames == null || rawNames.isEmpty()) {
            return List.of();
        }
        Map<String, String> deduped = new LinkedHashMap<>();
        for (String raw : rawNames) {
            if (raw == null) {
                continue;
            }
            String cleaned = raw.trim()
                    .replaceFirst("^#+", "")
                    .replaceAll("\\s+", " ")
                    .trim();
            if (cleaned.isEmpty()) {
                continue;
            }
            if (cleaned.length() > MAX_TAG_LENGTH) {
                throw new IllegalArgumentException(MessageConstant.TAG_TOO_LONG);
            }
            String lower = cleaned.toLowerCase(Locale.ROOT);
            deduped.putIfAbsent(lower, cleaned);
            if (deduped.size() > MAX_TAGS_PER_POST) {
                throw new IllegalArgumentException(MessageConstant.TAG_LIMIT_EXCEEDED);
            }
        }
        return new ArrayList<>(deduped.values());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<TagVO> bindPostTags(Long postId, Integer categoryId, List<String> rawNames, boolean isPrivate) {
        if (postId == null) {
            return List.of();
        }
        // 先清理旧关联（避免重发或幂等重试时重复绑定）
        List<PostTag> existingLinks = postTagMapper.selectList(
                new LambdaQueryWrapper<PostTag>().eq(PostTag::getPostId, postId));
        if (!existingLinks.isEmpty()) {
            List<Long> oldTagIds = existingLinks.stream().map(PostTag::getTagId).distinct().toList();
            postTagMapper.delete(new LambdaQueryWrapper<PostTag>().eq(PostTag::getPostId, postId));
            if (!isPrivate) {
                adjustUseCounts(oldTagIds, -1);
            }
        }

        List<String> normalized = normalizeTagNames(rawNames);
        if (normalized.isEmpty()) {
            evictHotTagCache();
            return List.of();
        }

        List<TagVO> boundTags = new ArrayList<>();
        for (String tagName : normalized) {
            Tag tag = findOrCreateTag(tagName, categoryId, isPrivate);
            if (!Boolean.TRUE.equals(tag.getEnabled())) {
                continue;
            }
            try {
                postTagMapper.insert(new PostTag(postId, tag.getId()));
            } catch (DuplicateKeyException ignored) {
                // 并发保护：唯一索引防重
            }
            boundTags.add(toVO(tag));
        }
        evictHotTagCache();
        return boundTags;
    }

    private Tag findOrCreateTag(String tagName, Integer categoryId, boolean isPrivate) {
        Tag existing = tagMapper.selectOne(
                new LambdaQueryWrapper<Tag>().eq(Tag::getName, tagName).last("LIMIT 1"));
        if (existing != null) {
            if (!isPrivate && Boolean.TRUE.equals(existing.getEnabled())) {
                tagMapper.update(null, new LambdaUpdateWrapper<Tag>()
                        .eq(Tag::getId, existing.getId())
                        .setSql("use_count = use_count + 1"));
                existing.setUseCount((existing.getUseCount() == null ? 0 : existing.getUseCount()) + 1);
            }
            return existing;
        }
        Tag created = new Tag();
        created.setName(tagName);
        created.setCategoryId(categoryId);
        created.setUseCount(isPrivate ? 0 : 1);
        created.setIsHot(false);
        created.setEnabled(true);
        try {
            tagMapper.insert(created);
            return created;
        } catch (DuplicateKeyException e) {
            Tag concurrent = tagMapper.selectOne(
                    new LambdaQueryWrapper<Tag>().eq(Tag::getName, tagName).last("LIMIT 1"));
            if (concurrent != null && !isPrivate && Boolean.TRUE.equals(concurrent.getEnabled())) {
                tagMapper.update(null, new LambdaUpdateWrapper<Tag>()
                        .eq(Tag::getId, concurrent.getId())
                        .setSql("use_count = use_count + 1"));
                concurrent.setUseCount((concurrent.getUseCount() == null ? 0 : concurrent.getUseCount()) + 1);
            }
            return concurrent;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onPostPrivacyChanged(Long postId, boolean wasPrivate, boolean nowPrivate) {
        if (postId == null || wasPrivate == nowPrivate) {
            return;
        }
        List<Long> tagIds = postTagMapper.selectList(
                        new LambdaQueryWrapper<PostTag>().eq(PostTag::getPostId, postId))
                .stream()
                .map(PostTag::getTagId)
                .distinct()
                .toList();
        if (tagIds.isEmpty()) {
            return;
        }
        adjustUseCounts(tagIds, wasPrivate ? 1 : -1);
        evictHotTagCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onPostDeleted(Long postId, boolean wasPrivate) {
        if (postId == null) {
            return;
        }
        List<PostTag> links = postTagMapper.selectList(
                new LambdaQueryWrapper<PostTag>().eq(PostTag::getPostId, postId));
        if (links.isEmpty()) {
            return;
        }
        postTagMapper.delete(new LambdaQueryWrapper<PostTag>().eq(PostTag::getPostId, postId));
        if (!wasPrivate) {
            List<Long> tagIds = links.stream().map(PostTag::getTagId).distinct().toList();
            adjustUseCounts(tagIds, -1);
        }
        evictHotTagCache();
    }

    private void adjustUseCounts(Collection<Long> tagIds, int delta) {
        if (tagIds == null || tagIds.isEmpty() || delta == 0) {
            return;
        }
        String sql = delta > 0
                ? "use_count = use_count + " + delta
                : "use_count = GREATEST(use_count - " + (-delta) + ", 0)";
        tagMapper.update(null, new LambdaUpdateWrapper<Tag>()
                .in(Tag::getId, tagIds)
                .setSql(sql));
    }

    @Override
    public Map<Long, List<TagVO>> getTagsByPostIds(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        List<PostTag> links = postTagMapper.selectList(
                new LambdaQueryWrapper<PostTag>().in(PostTag::getPostId, postIds));
        if (links.isEmpty()) {
            return Map.of();
        }
        Set<Long> tagIds = links.stream().map(PostTag::getTagId).collect(Collectors.toSet());
        Map<Long, Tag> tagMap = new HashMap<>();
        tagMapper.selectBatchIds(tagIds).forEach(t -> {
            if (Boolean.TRUE.equals(t.getEnabled())) {
                tagMap.put(t.getId(), t);
            }
        });
        Map<Long, List<TagVO>> result = new HashMap<>();
        for (PostTag link : links) {
            Tag tag = tagMap.get(link.getTagId());
            if (tag != null) {
                result.computeIfAbsent(link.getPostId(), k -> new ArrayList<>()).add(toVO(tag));
            }
        }
        return result;
    }

    @Override
    public Map<Long, List<Long>> getTagIdsByPostIds(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        List<PostTag> links = postTagMapper.selectList(
                new LambdaQueryWrapper<PostTag>().in(PostTag::getPostId, postIds));
        Map<Long, List<Long>> result = new HashMap<>();
        for (PostTag link : links) {
            result.computeIfAbsent(link.getPostId(), k -> new ArrayList<>()).add(link.getTagId());
        }
        return result;
    }

    @Override
    public List<TagVO> getHotTags(Integer categoryId, Integer limit) {
        int safeLimit = Math.max(1, Math.min(limit == null ? 20 : limit, 50));
        String cacheKey = KeyConstant.TAG_HOT_KEY + (categoryId == null ? "all" : categoryId) + ":" + safeLimit;
        String cachedJson = redisUtil.strings().opsForValue().get(cacheKey);
        if (StringUtils.isNotBlank(cachedJson)) {
            return JSON.parseArray(cachedJson, TagVO.class);
        }
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<Tag>()
                .eq(Tag::getEnabled, true)
                .orderByDesc(Tag::getIsHot)
                .orderByDesc(Tag::getUseCount)
                .orderByAsc(Tag::getId)
                .last("LIMIT " + safeLimit);
        if (categoryId != null) {
            wrapper.eq(Tag::getCategoryId, categoryId);
        }
        List<TagVO> tags = tagMapper.selectList(wrapper).stream().map(this::toVO).toList();
        redisUtil.strings().opsForValue().set(cacheKey, JSON.toJSONString(tags),
                CacheTtl.TAG_HOT_MINUTES, TimeUnit.MINUTES);
        return tags;
    }

    @Override
    public List<TagVO> searchTags(String keyword, Integer categoryId, Integer limit) {
        int safeLimit = Math.max(1, Math.min(limit == null ? 15 : limit, 30));
        String trimmed = keyword == null ? "" : keyword.trim().replaceFirst("^#+", "");
        if (trimmed.isEmpty()) {
            return getHotTags(categoryId, safeLimit);
        }
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<Tag>()
                .eq(Tag::getEnabled, true)
                .like(Tag::getName, trimmed)
                .orderByDesc(Tag::getIsHot)
                .orderByDesc(Tag::getUseCount)
                .last("LIMIT " + safeLimit);
        if (categoryId != null) {
            wrapper.and(w -> w.eq(Tag::getCategoryId, categoryId).or().isNull(Tag::getCategoryId));
        }
        return tagMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    @Override
    public Tag resolveTag(Long tagId, String tagName) {
        if (tagId != null) {
            Tag tag = tagMapper.selectById(tagId);
            return (tag != null && Boolean.TRUE.equals(tag.getEnabled())) ? tag : null;
        }
        if (StringUtils.isBlank(tagName)) {
            return null;
        }
        String cleaned = tagName.trim().replaceFirst("^#+", "");
        if (cleaned.isEmpty()) {
            return null;
        }
        return tagMapper.selectOne(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getName, cleaned)
                .eq(Tag::getEnabled, true)
                .last("LIMIT 1"));
    }

    @Override
    public void evictHotTagCache() {
        try {
            Set<String> keys = redisUtil.scan(KeyConstant.TAG_HOT_KEY + "*");
            if (!keys.isEmpty()) {
                redisUtil.delete(keys);
            }
        } catch (Exception e) {
            log.warn("清理热门标签缓存失败: {}", e.getMessage());
        }
    }

    private TagVO toVO(Tag tag) {
        return TagVO.builder()
                .id(tag.getId())
                .name(tag.getName())
                .categoryId(tag.getCategoryId())
                .useCount(tag.getUseCount() == null ? 0 : tag.getUseCount())
                .isHot(Boolean.TRUE.equals(tag.getIsHot()))
                .build();
    }
}
