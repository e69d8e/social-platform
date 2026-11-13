package com.li.socialplatform.pojo.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 帖子标签视图对象
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TagVO implements Serializable {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    private String name;

    private Integer categoryId;

    private Integer useCount;

    private Boolean isHot;
}
