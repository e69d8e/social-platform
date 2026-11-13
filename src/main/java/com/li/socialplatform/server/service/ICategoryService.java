package com.li.socialplatform.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.li.socialplatform.pojo.dto.CategoryDTO;
import com.li.socialplatform.pojo.entity.Category;
import com.li.socialplatform.pojo.entity.Result;

public interface ICategoryService extends IService<Category> {
    Result getCategory();

    Result listCategories(boolean includeDisabled);

    Result getCategoryDetail(Integer id);

    Result getCategoryTags(Integer categoryId, Integer limit);

    Result createCategory(CategoryDTO categoryDTO);

    Result updateCategory(CategoryDTO categoryDTO);

    Result toggleCategoryEnabled(Integer id);

    void evictCategoryCache();
}
