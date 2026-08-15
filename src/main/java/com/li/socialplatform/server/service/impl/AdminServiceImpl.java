package com.li.socialplatform.server.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.li.socialplatform.common.constant.AuthorityConstant;
import com.li.socialplatform.common.constant.MessageConstant;
import com.li.socialplatform.common.utils.BanCacheUtil;
import com.li.socialplatform.common.utils.DataCacheUtil;
import com.li.socialplatform.common.utils.UserIdUtil;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.pojo.entity.User;
import com.li.socialplatform.pojo.vo.ChartItemVO;
import com.li.socialplatform.pojo.vo.DashboardSummaryVO;
import com.li.socialplatform.pojo.vo.UserVO;
import com.li.socialplatform.server.mapper.DashboardMapper;
import com.li.socialplatform.server.mapper.UserMapper;
import com.li.socialplatform.server.repository.UserElasticsearchRepository;
import com.li.socialplatform.server.service.IAdminService;
import lombok.RequiredArgsConstructor;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;


/**
 * @author e69d8e
 * @since 2025/12/10 14:04
 */
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements IAdminService {

    private final UserMapper userMapper;
    private final UserIdUtil userIdUtil;
    private final DataCacheUtil dataCacheUtil;
    private final BanCacheUtil banCacheUtil;
    private final UserElasticsearchRepository userElasticsearchRepository;
    private final DashboardMapper dashboardMapper;
    private final ElasticsearchOperations elasticsearchOperations;

    @Transactional
    @Override
    public Result banUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return Result.error(MessageConstant.USER_NOT_EXIST);
        }
        if (user.getEnabled()) {
            banCacheUtil.addBanUser(userIdUtil.getUserId(), id);
        } else {
            banCacheUtil.removeBanUser(id);
        }
        user.setEnabled(!user.getEnabled());
        userMapper.updateById(user);
        // 同步到Elasticsearch
        userElasticsearchRepository.save(user);
        return Result.ok(MessageConstant.BAN_SUCCESS, "");
    }

    @Override
    public Result getBanUser(Integer pageNum, Integer pageSize) {
        long start = ((long) (pageNum - 1) * pageSize);
        long end = start + pageSize - 1;
        Long total = banCacheUtil.getBanUserTotal();
        if (total == null || total == 0) {
            return Result.ok(List.of(), 0L);
        }
        if (start > total) {
            return Result.ok(List.of(), 0L);
        }
        if (end > total) {
            end = total - 1;
        }
        Set<Object> members = banCacheUtil.getBanUserIds(start, end);
        if (members == null || members.isEmpty()) {
            return Result.ok(List.of(), 0L);
        }
        List<Long> ids = members.stream().map(member -> Long.valueOf(member.toString())).toList();
        // 批量查询用户
        Map<Long, User> userMap = new HashMap<>();
        userMapper.selectBatchIds(ids).forEach(u -> userMap.put(u.getId(), u));
        Long currentUserId = userIdUtil.getUserId();
        List<UserVO> users = new ArrayList<>();
        for (Long id : ids) {
            User user = userMap.get(id);
            if (user == null) continue;
            UserVO userVO = BeanUtil.copyProperties(user, UserVO.class);
            userVO.setEnabled(false);
            userVO.setFollowed(currentUserId != null && dataCacheUtil.isFollowed(currentUserId, id));
            userVO.setFansCount(dataCacheUtil.getFollowerCount(user.getId()));
            users.add(userVO);
        }
        return Result.ok(users, total);
    }

    @Override
    public Result setReviewer(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return Result.error(MessageConstant.USER_NOT_EXIST);
        }
        user.setAuthorityId(AuthorityConstant.REVIEWER);
        return userMapper.updateById(user) > 0 ? Result.ok(MessageConstant.SET_SUCCESS, "") : Result.error(MessageConstant.SET_FAIL);
    }

    @Override
    public Result setUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return Result.error(MessageConstant.USER_NOT_EXIST);
        }
        user.setAuthorityId(AuthorityConstant.USER);
        return userMapper.updateById(user) > 0 ? Result.ok(MessageConstant.SET_SUCCESS, "") : Result.error(MessageConstant.SET_FAIL);
    }

    @Override
    public Result searchBanUser(String keyword, Integer pageNum, Integer pageSize) {
        // 从 Redis 获取所有封禁用户 ID
        Long banTotal = banCacheUtil.getBanUserTotal();
        if (banTotal == null || banTotal == 0) {
            return Result.ok(List.of(), 0L);
        }
        Set<Object> allBanMembers = banCacheUtil.getBanUserIds(0, banTotal - 1);
        if (allBanMembers == null || allBanMembers.isEmpty()) {
            return Result.ok(List.of(), 0L);
        }
        List<Long> banIds = allBanMembers.stream()
                .map(member -> Long.valueOf(member.toString()))
                .toList();

        // 构建 ES 查询：关键词匹配 username/nickname + 过滤封禁用户 ID
        List<String> idStrings = banIds.stream().map(String::valueOf).toList();
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b
                        .must(m -> m.multiMatch(mm -> mm.fields("username.edge", "nickname.edge").query(keyword)))
                        .filter(f -> f.terms(t -> t.field("id").terms(tv -> tv.value(idStrings.stream().map(co.elastic.clients.elasticsearch._types.FieldValue::of).toList()))))))
                .withPageable(PageRequest.of(pageNum - 1, pageSize))
                .withSort(Sort.by(Sort.Direction.DESC, "createTime"))
                .build();

        SearchHits<User> hits = elasticsearchOperations.search(query, User.class);
        long total = hits.getTotalHits();
        List<UserVO> userVOS = new ArrayList<>();
        Long currentUserId = userIdUtil.getUserId();
        for (SearchHit<User> hit : hits) {
            User user = hit.getContent();
            UserVO userVO = BeanUtil.copyProperties(user, UserVO.class);
            userVO.setEnabled(false);
            userVO.setFollowed(currentUserId != null && dataCacheUtil.isFollowed(currentUserId, user.getId()));
            userVO.setFansCount(dataCacheUtil.getFollowerCount(user.getId()));
            userVOS.add(userVO);
        }
        return Result.ok(userVOS, total);
    }

    @Override
    public Result getDashboardStats(Integer days) {
        if (days == null || days <= 0) {
            days = 30;
        }
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(days);
        String startDate = start.format(DateTimeFormatter.ISO_LOCAL_DATE);

        // 补齐缺失日期，保证曲线连续（无数据的日期/周补 0）
        List<ChartItemVO> dailyPosts = padDaily(dashboardMapper.countDailyPosts(startDate), start, today);
        List<ChartItemVO> weeklyNewUsers = padWeekly(dashboardMapper.countWeeklyNewUsers(startDate), start, today);
        List<ChartItemVO> dailyActiveUsers = padDaily(dashboardMapper.countDailyActiveUsers(startDate), start, today);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("summary", buildSummary(today, dailyPosts, dailyActiveUsers));
        data.put("dailyPosts", dailyPosts);
        data.put("weeklyNewUsers", weeklyNewUsers);
        data.put("dailyActiveUsers", dailyActiveUsers);

        return Result.ok(data);
    }

    /**
     * 组装概览卡片数据
     */
    private DashboardSummaryVO buildSummary(LocalDate today, List<ChartItemVO> dailyPosts, List<ChartItemVO> dailyActiveUsers) {
        String todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
        return new DashboardSummaryVO(
                dashboardMapper.countTotalUsers(),
                dashboardMapper.countTotalPosts(),
                dashboardMapper.countTotalComments(),
                dashboardMapper.countTotalLikes(),
                dashboardMapper.countTotalViews(),
                dashboardMapper.countNewUsersOn(todayStr),
                valueOf(dailyPosts, todayStr),
                valueOf(dailyActiveUsers, todayStr)
        );
    }

    /**
     * 从补齐后的日序列中取某天的数值
     */
    private long valueOf(List<ChartItemVO> series, String date) {
        for (ChartItemVO item : series) {
            if (date.equals(item.getDate())) {
                return item.getCount() == null ? 0L : item.getCount();
            }
        }
        return 0L;
    }

    /**
     * 补齐每日序列：区间内无数据的日期补 0
     */
    private List<ChartItemVO> padDaily(List<ChartItemVO> raw, LocalDate start, LocalDate end) {
        Map<LocalDate, Long> countByDate = new HashMap<>();
        for (ChartItemVO item : raw) {
            countByDate.put(LocalDate.parse(item.getDate()), item.getCount() == null ? 0L : item.getCount());
        }
        List<ChartItemVO> result = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            result.add(new ChartItemVO(d.toString(), countByDate.getOrDefault(d, 0L)));
        }
        return result;
    }

    /**
     * 补齐每周序列：区间内无新增用户的周补 0，周标签使用 ISO-8601（yyyy-Www）
     */
    private List<ChartItemVO> padWeekly(List<ChartItemVO> raw, LocalDate start, LocalDate end) {
        Map<String, Long> countByWeek = new HashMap<>();
        for (ChartItemVO item : raw) {
            countByWeek.put(item.getDate(), item.getCount() == null ? 0L : item.getCount());
        }
        // 按天遍历，生成去重且有序的周标签集合
        Set<String> weekLabels = new LinkedHashSet<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            weekLabels.add(isoWeekLabel(d));
        }
        List<ChartItemVO> result = new ArrayList<>();
        for (String label : weekLabels) {
            result.add(new ChartItemVO(label, countByWeek.getOrDefault(label, 0L)));
        }
        return result;
    }

    /**
     * 计算 ISO-8601 周标签，与 SQL 中 DATE_FORMAT(create_time, '%x-W%v') 保持一致
     */
    private String isoWeekLabel(LocalDate date) {
        int weekBasedYear = date.get(WeekFields.ISO.weekBasedYear());
        int week = date.get(WeekFields.ISO.weekOfWeekBasedYear());
        return String.format("%d-W%02d", weekBasedYear, week);
    }
}
