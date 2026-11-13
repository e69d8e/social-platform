package com.li.socialplatform.recommend;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 精排打分结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoredPost {
    private CandidatePost post;
    private double score;
    private double qualityScore;
    private double freshnessScore;
    private double categoryAffinity;
    private double tagAffinity;
    private double authorAffinity;
    private boolean seen;
    private boolean exploration;
    private String reason;
}
