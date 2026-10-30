package com.li.socialplatform.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.li.socialplatform.common.utils.UserIdUtil;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.pojo.entity.SearchHistory;
import com.li.socialplatform.server.mapper.SearchHistoryMapper;
import com.li.socialplatform.server.service.ISearchHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchHistoryServiceImpl implements ISearchHistoryService {

    private final SearchHistoryMapper searchHistoryMapper;
    private final UserIdUtil userIdUtil;

    /** 关键词入库长度上限（表字段 255，历史列表只需要可辨认的前缀） */
    private static final int KEYWORD_MAX_LENGTH = 50;

    @Transactional
    @Override
    public void recordSearch(String keyword, Integer type) {
        Long userId = userIdUtil.getUserId();
        if (userId == null || keyword == null) {
            return;
        }
        String kw = keyword.trim();
        if (kw.isEmpty()) {
            return;
        }
        if (kw.length() > KEYWORD_MAX_LENGTH) {
            kw = kw.substring(0, KEYWORD_MAX_LENGTH);
        }

        // 搜索历史按「用户 + 关键词」去重，type 只记录最近一次搜索入口。
        // 之前的「先查再写」在并发下会产生重复行：搜索页会同时发起帖子/用户两个请求，
        // 两个线程都查不到记录 → 都插入。这里改为直接插入，由唯一索引 uk_user_keyword
        // 兜底并发（命中重复则退化为更新时间）。
        try {
            SearchHistory sh = new SearchHistory();
            sh.setUserId(userId);
            sh.setKeyword(kw);
            sh.setType(type);
            searchHistoryMapper.insert(sh);
            return;
        } catch (DuplicateKeyException ignored) {
            // 已有同关键词记录，走更新分支
        }
        searchHistoryMapper.update(null, new LambdaUpdateWrapper<SearchHistory>()
                .eq(SearchHistory::getUserId, userId)
                .eq(SearchHistory::getKeyword, kw)
                .set(SearchHistory::getType, type)
                .set(SearchHistory::getCreateTime, LocalDateTime.now()));
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
