package com.li.socialplatform.server.service;

import com.li.socialplatform.pojo.entity.Result;

public interface ISearchHistoryService {

    void recordSearch(String keyword, Integer type);

    Result deleteSearchRecord(Long id);

    Result clearSearchRecords();

    Result getSearchRecords(Integer pageNum, Integer pageSize);
}
