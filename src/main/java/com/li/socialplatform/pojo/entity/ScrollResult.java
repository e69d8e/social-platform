package com.li.socialplatform.pojo.entity;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ScrollResult<T> implements Serializable {
    private List<T> list;
    private Long minTime;
    private Integer offset;
    private Boolean hasMore;
    private String mode;
    private Long seed;
}