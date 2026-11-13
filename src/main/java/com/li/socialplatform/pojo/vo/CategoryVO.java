package com.li.socialplatform.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 分类详情视图对象（含帖子数量与热门标签）
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CategoryVO implements Serializable {
    private Integer id;
    private String name;
    private String description;
    private String icon;
    private String color;
    private Integer sortOrder;
    private Boolean enabled;
    private Long postCount;
    private List<TagVO> hotTags;
}
