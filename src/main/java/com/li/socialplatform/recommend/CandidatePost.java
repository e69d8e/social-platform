package com.li.socialplatform.recommend;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 参与推荐重排的候选帖子快照（与具体存储解耦，便于单元测试）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidatePost {
    private Long id;
    private Long userId;
    private Integer categoryId;
    private String categoryName;
    @Builder.Default
    private List<Long> tagIds = List.of();
    @Builder.Default
    private Map<Long, String> tagNamesById = Map.of();
    private Integer likeCount;
    private Integer starCount;
    private Integer commentCount;
    private Integer viewCount;
    private LocalDateTime createTime;
    private Boolean enabled;
    private Boolean isPrivate;
    @Builder.Default
    private Boolean authorEnabled = Boolean.TRUE;
}
