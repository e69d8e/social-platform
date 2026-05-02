package com.li.socialplatform.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.mapper.CategoryMapper;
import com.li.socialplatform.pojo.entity.Category;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.service.ICategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * @author e69d8e
 * @since 2025/12/12 22:26
 */
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements ICategoryService {
    private final CategoryMapper categoryMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Result getCategory() {
        // 从缓存中取出
        String key = KeyConstant.CATEGORY_LIST_KEY;
        Long size = redisTemplate.opsForList().size(key);
        if (size != null && size > 0) {
            return Result.ok(Objects.requireNonNull(redisTemplate.opsForList().range(key, 0, -1))
                    .stream().map(item -> BeanUtil.copyProperties(item, Category.class)).toList());
        }
        // 如果缓存没有 则从数据库查 返回并加入缓存
        List<Category> categories = categoryMapper.selectList(new LambdaQueryWrapper<>());
        for (Category category : categories) {
            redisTemplate.opsForList().rightPush(KeyConstant.CATEGORY_LIST_KEY, category);
        }
        return Result.ok(categories);
    }
}
