package com.li.socialplatform.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.li.socialplatform.pojo.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * @author e69d8e
 * @since 2025/12/9 16:03
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

    /**
     * 统计各分类下当前公开且作者正常的帖子数
     */
    @Select("""
            SELECT p.category_id AS categoryId, COUNT(*) AS postCount
            FROM post p
            INNER JOIN user u ON p.user_id = u.id
            WHERE p.enabled = 1
              AND u.enabled = 1
              AND (p.is_private IS NULL OR p.is_private = 0)
            GROUP BY p.category_id
            """)
    List<Map<String, Object>> countPublicPostsByCategory();
}
