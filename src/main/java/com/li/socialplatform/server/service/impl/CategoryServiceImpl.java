package com.li.socialplatform.server.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.socialplatform.common.constant.CacheTtl;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.common.utils.RedisUtil;
import com.li.socialplatform.server.mapper.CategoryMapper;
import com.li.socialplatform.pojo.entity.Category;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.server.service.ICategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author e69d8e
 * @since 2025/12/12 22:26
 */
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements ICategoryService {
    private final CategoryMapper categoryMapper;
    private final RedisUtil redisUtil;

    @Override
    @SuppressWarnings("unchecked")
    public Result getCategory() {
        String key = KeyConstant.CATEGORY_LIST_KEY;
        // 从缓存中取出
        List<Object> cached = redisUtil.lists().range(key, 0, -1);
        if (cached != null && !cached.isEmpty()) {
            return Result.ok(cached.stream()
                    .map(item -> BeanUtil.copyProperties(item, Category.class)).toList());
        }
        // 缓存未命中则查库，整表一次性写入并统一设置过期时间
        List<Category> categories = categoryMapper.selectList(new LambdaQueryWrapper<>());
        if (!categories.isEmpty()) {
            redisUtil.lists().rightPushAll(key, categories.toArray());
            redisUtil.expire(key, CacheTtl.CATEGORY_MINUTES, TimeUnit.MINUTES);
        }
        return Result.ok(categories);
    }
}
