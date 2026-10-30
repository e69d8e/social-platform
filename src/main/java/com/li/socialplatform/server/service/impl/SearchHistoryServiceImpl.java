package com.li.socialplatform.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.li.socialplatform.common.utils.UserIdUtil;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.pojo.entity.SearchHistory;
import com.li.socialplatform.server.mapper.SearchHistoryMapper;
import com.li.socialplatform.server.service.ISearchHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchHistoryServiceImpl implements ISearchHistoryService {

    private final SearchHistoryMapper searchHistoryMapper;
    private final UserIdUtil userIdUtil;

    @Transactional
    @Override
    public void recordSearch(String keyword, Integer type) {
        Long userId = userIdUtil.getUserId();
        if (userId == null) {
            return;
        }
        // 搜索历史不再区分帖子/用户：仅按关键词去重，type 仅记录最近一次搜索入口
        List<SearchHistory> list = searchHistoryMapper.selectList(new LambdaQueryWrapper<SearchHistory>()
                .eq(SearchHistory::getUserId, userId)
                .eq(SearchHistory::getKeyword, keyword));
        if (!list.isEmpty()) {
            SearchHistory exist = list.get(0);
            exist.setCreateTime(LocalDateTime.now());
            exist.setType(type);
            searchHistoryMapper.updateById(exist);
            for (int i = 1; i < list.size(); i++) {
                searchHistoryMapper.deleteById(list.get(i).getId());
            }
        } else {
            SearchHistory sh = new SearchHistory();
            sh.setUserId(userId);
            sh.setKeyword(keyword);
            sh.setType(type);
            searchHistoryMapper.insert(sh);
        }
    }

    @Override
    public Result deleteSearchRecord(Long id) {
        Long userId = userIdUtil.getUserId();
        if (userId == null) {
            return Result.ok();
        }
        SearchHistory sh = searchHistoryMapper.selectOne(new LambdaQueryWrapper<SearchHistory>()
                .eq(SearchHistory::getId, id)
                .eq(SearchHistory::getUserId, userId));
        if (sh == null) {
            return Result.ok();
        }
        searchHistoryMapper.deleteById(id);
        return Result.ok();
    }

    @Override
    public Result clearSearchRecords() {
        Long userId = userIdUtil.getUserId();
        if (userId == null) {
            return Result.ok();
        }
        searchHistoryMapper.delete(new LambdaQueryWrapper<SearchHistory>()
                .eq(SearchHistory::getUserId, userId));
        return Result.ok();
    }

    @Override
    public Result getSearchRecords(Integer pageNum, Integer pageSize) {
        Long userId = userIdUtil.getUserId();
        if (userId == null) {
            return Result.ok();
        }
        IPage<SearchHistory> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SearchHistory> wrapper = new LambdaQueryWrapper<SearchHistory>()
                .eq(SearchHistory::getUserId, userId)
                .orderByDesc(SearchHistory::getCreateTime);
        IPage<SearchHistory> result = searchHistoryMapper.selectPage(page, wrapper);
        return Result.ok(result.getRecords(), result.getTotal());
    }
}
