package com.li.socialplatform.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 数据面板概览卡片数据
 *
 * @author e69d8e
 * @since 2026/06/02
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DashboardSummaryVO implements Serializable {
    /**
     * 用户总数
     */
    private Long totalUsers;

    /**
     * 帖子总数
     */
    private Long totalPosts;

    /**
     * 评论总数
     */
    private Long totalComments;

    /**
     * 点赞总数
     */
    private Long totalLikes;

    /**
     * 浏览量总数（帖子浏览数求和）
     */
    private Long totalViews;

    /**
     * 今日新增用户
     */
    private Long todayNewUsers;

    /**
     * 今日新增帖子
     */
    private Long todayPosts;

    /**
     * 今日活跃用户
     */
    private Long todayActiveUsers;
}
