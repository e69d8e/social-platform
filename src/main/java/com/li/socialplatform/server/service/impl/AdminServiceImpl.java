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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
        List<UserVO> users = new ArrayList<>();
        for (Long id : ids) {
            User user = userMapper.selectById(id);
            UserVO userVO = BeanUtil.copyProperties(user, UserVO.class);
            userVO.setEnabled(false);
            Long currentUserId = userIdUtil.getUserId();
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
                        .must(m -> m.multiMatch(mm -> mm.fields("username", "nickname").query(keyword)))
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
        String startDate = LocalDate.now().minusDays(days).format(DateTimeFormatter.ISO_LOCAL_DATE);

        List<ChartItemVO> dailyPosts = dashboardMapper.countDailyPosts(startDate);
        List<ChartItemVO> weeklyNewUsers = dashboardMapper.countWeeklyNewUsers(startDate);
        List<ChartItemVO> dailyActiveUsers = dashboardMapper.countDailyActiveUsers(startDate);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dailyPosts", dailyPosts);
        data.put("weeklyNewUsers", weeklyNewUsers);
        data.put("dailyActiveUsers", dailyActiveUsers);

        return Result.ok(data);
    }
}
