package com.li.socialplatform.server.mapper;

import com.li.socialplatform.pojo.vo.ChartItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 管理后台数据统计 Mapper
 *
 * @author e69d8e
 * @since 2026/06/02
 */
@Mapper
public interface DashboardMapper {

    /**
     * 按天统计发帖数量
     */
    @Select("""
            SELECT DATE(create_time) AS date, COUNT(*) AS count
            FROM post
            WHERE create_time >= #{startDate}
            GROUP BY DATE(create_time)
            ORDER BY date
            """)
    List<ChartItemVO> countDailyPosts(@Param("startDate") String startDate);

    /**
     * 按周统计新增用户数量
     */
    @Select("""
            SELECT CONCAT(yr, '-W', LPAD(wk + 1, 2, '0')) AS date, cnt AS count
            FROM (
                SELECT YEAR(create_time) AS yr, WEEK(create_time, 1) AS wk, COUNT(*) AS cnt
                FROM user
                WHERE create_time >= #{startDate}
                GROUP BY YEAR(create_time), WEEK(create_time, 1)
            ) t
            ORDER BY yr, wk
            """)
    List<ChartItemVO> countWeeklyNewUsers(@Param("startDate") String startDate);

    /**
     * 按天统计活跃用户数量（发帖 + 评论 + 点赞 + 私信的去重用户数）
     */
    @Select("""
            SELECT DATE(t.create_time) AS date, COUNT(DISTINCT t.user_id) AS count
            FROM (
                SELECT user_id, create_time FROM post WHERE create_time >= #{startDate}
                UNION ALL
                SELECT user_id, create_time FROM comment WHERE create_time >= #{startDate}
                UNION ALL
                SELECT user_id, create_time FROM like_record WHERE create_time >= #{startDate}
                UNION ALL
                SELECT sender_id AS user_id, create_time FROM private_message WHERE create_time >= #{startDate}
            ) t
            GROUP BY DATE(t.create_time)
            ORDER BY date
            """)
    List<ChartItemVO> countDailyActiveUsers(@Param("startDate") String startDate);
}
