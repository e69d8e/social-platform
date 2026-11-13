package com.li.socialplatform.server.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.socialplatform.common.constant.CacheTtl;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.common.constant.MessageConstant;
import com.li.socialplatform.common.utils.RedisUtil;
import com.li.socialplatform.pojo.dto.CategoryDTO;
import com.li.socialplatform.pojo.entity.Category;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.pojo.vo.CategoryVO;
import com.li.socialplatform.pojo.vo.TagVO;
import com.li.socialplatform.server.mapper.CategoryMapper;
import com.li.socialplatform.server.service.ICategoryService;
import com.li.socialplatform.server.service.ITagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements ICategoryService {

    private static final int DEFAULT_CATEGORY_ID = 1;

    private final CategoryMapper categoryMapper;
    private final ITagService tagService;
    private final RedisUtil redisUtil;

    @Override
    public Result getCategory() {
        return listCategories(false);
    }

    @Override
    public Result listCategories(boolean includeDisabled) {
        if (!includeDisabled) {
            String cachedJson = redisUtil.strings().opsForValue().get(KeyConstant.CATEGORY_VO_LIST_KEY);
            if (StringUtils.isNotBlank(cachedJson)) {
                return Result.ok(JSON.parseArray(cachedJson, CategoryVO.class));
            }
        }

        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSortOrder)
                .orderByAsc(Category::getId);
        if (!includeDisabled) {
            wrapper.eq(Category::getEnabled, true);
        }
        List<Category> categories = categoryMapper.selectList(wrapper);
        if (categories.isEmpty()) {
            return Result.ok(List.of());
        }

        Map<Integer, Long> postCountMap = loadPostCountMap();
        List<CategoryVO> voList = categories.stream()
                .map(c -> toCategoryVO(c, postCountMap.getOrDefault(c.getId(), 0L), true))
                .toList();

        if (!includeDisabled) {
            redisUtil.strings().opsForValue().set(
                    KeyConstant.CATEGORY_VO_LIST_KEY,
                    JSON.toJSONString(voList),
                    CacheTtl.CATEGORY_MINUTES,
                    TimeUnit.MINUTES);
        }
        return Result.ok(voList);
    }

    @Override
    public Result getCategoryDetail(Integer id) {
        if (id == null) {
            return Result.error(MessageConstant.ID_IS_NULL);
        }
        Category category = categoryMapper.selectById(id);
        if (category == null || !Boolean.TRUE.equals(category.getEnabled())) {
            return Result.error(MessageConstant.CATEGORY_NOT_EXIST);
        }
        Map<Integer, Long> postCountMap = loadPostCountMap();
        return Result.ok(toCategoryVO(category, postCountMap.getOrDefault(id, 0L), true));
    }

    @Override
    public Result getCategoryTags(Integer categoryId, Integer limit) {
        if (categoryId == null) {
            return Result.ok(tagService.getHotTags(null, limit));
        }
        return Result.ok(tagService.getHotTags(categoryId, limit == null ? 15 : limit));
    }

    @Override
    public Result createCategory(CategoryDTO dto) {
        String name = dto.getName().trim();
        Category existing = categoryMapper.selectOne(
                new LambdaQueryWrapper<Category>().eq(Category::getName, name).last("LIMIT 1"));
        if (existing != null) {
            return Result.error(MessageConstant.CATEGORY_NAME_EXISTS);
        }
        Category category = new Category();
        category.setName(name);
        category.setDescription(StringUtils.defaultString(dto.getDescription(), "").trim());
        category.setIcon(StringUtils.isNotBlank(dto.getIcon()) ? dto.getIcon().trim() : "📌");
        category.setColor(StringUtils.isNotBlank(dto.getColor()) ? dto.getColor().trim() : "#D97757");
        category.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 100);
        category.setEnabled(dto.getEnabled() == null || dto.getEnabled());
        categoryMapper.insert(category);
        evictCategoryCache();
        return Result.ok("创建分类成功", toCategoryVO(category, 0L, false));
    }

    @Override
    public Result updateCategory(CategoryDTO dto) {
        if (dto.getId() == null) {
            return Result.error(MessageConstant.ID_IS_NULL);
        }
        Category category = categoryMapper.selectById(dto.getId());
        if (category == null) {
            return Result.error(MessageConstant.CATEGORY_NOT_EXIST);
        }
        String name = dto.getName().trim();
        Category conflict = categoryMapper.selectOne(new LambdaQueryWrapper<Category>()
                .eq(Category::getName, name)
                .ne(Category::getId, dto.getId())
                .last("LIMIT 1"));
        if (conflict != null) {
            return Result.error(MessageConstant.CATEGORY_NAME_EXISTS);
        }
        if (Objects.equals(dto.getId(), DEFAULT_CATEGORY_ID) && Boolean.FALSE.equals(dto.getEnabled())) {
            return Result.error(MessageConstant.CATEGORY_CANNOT_DISABLE_DEFAULT);
        }
        category.setName(name);
        if (dto.getDescription() != null) {
            category.setDescription(dto.getDescription().trim());
        }
        if (dto.getIcon() != null) {
            category.setIcon(StringUtils.defaultIfBlank(dto.getIcon().trim(), "📌"));
        }
        if (dto.getColor() != null) {
            category.setColor(StringUtils.defaultIfBlank(dto.getColor().trim(), "#D97757"));
        }
        if (dto.getSortOrder() != null) {
            category.setSortOrder(dto.getSortOrder());
        }
        if (dto.getEnabled() != null) {
            category.setEnabled(dto.getEnabled());
        }
        categoryMapper.updateById(category);
        evictCategoryCache();
        return Result.ok(MessageConstant.UPDATE_SUCCESS, toCategoryVO(category, loadPostCountMap().getOrDefault(category.getId(), 0L), false));
    }

    @Override
    public Result toggleCategoryEnabled(Integer id) {
        if (id == null) {
            return Result.error(MessageConstant.ID_IS_NULL);
        }
        if (Objects.equals(id, DEFAULT_CATEGORY_ID)) {
            return Result.error(MessageConstant.CATEGORY_CANNOT_DISABLE_DEFAULT);
        }
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            return Result.error(MessageConstant.CATEGORY_NOT_EXIST);
        }
        category.setEnabled(!Boolean.TRUE.equals(category.getEnabled()));
        categoryMapper.updateById(category);
        evictCategoryCache();
        return Result.ok(MessageConstant.UPDATE_SUCCESS, category.getEnabled());
    }

    @Override
    public void evictCategoryCache() {
        redisUtil.delete(Arrays.asList(KeyConstant.CATEGORY_LIST_KEY, KeyConstant.CATEGORY_VO_LIST_KEY));
    }

    private Map<Integer, Long> loadPostCountMap() {
        Map<Integer, Long> postCountMap = new HashMap<>();
        try {
            List<Map<String, Object>> rows = categoryMapper.countPublicPostsByCategory();
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    Object catIdObj = row.get("categoryId");
                    Object countObj = row.get("postCount");
                    if (catIdObj != null && countObj != null) {
                        postCountMap.put(((Number) catIdObj).intValue(), ((Number) countObj).longValue());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("统计分类帖子数失败: {}", e.getMessage());
        }
        return postCountMap;
    }

    private CategoryVO toCategoryVO(Category c, Long postCount, boolean withHotTags) {
        List<TagVO> hotTags = withHotTags ? tagService.getHotTags(c.getId(), 4) : List.of();
        return CategoryVO.builder()
                .id(c.getId())
                .name(c.getName())
                .description(StringUtils.defaultString(c.getDescription(), ""))
                .icon(c.getIcon())
                .color(c.getColor())
                .sortOrder(c.getSortOrder() == null ? 100 : c.getSortOrder())
                .enabled(c.getEnabled() == null || c.getEnabled())
                .postCount(postCount == null ? 0L : postCount)
                .hotTags(hotTags)
                .build();
    }
}
