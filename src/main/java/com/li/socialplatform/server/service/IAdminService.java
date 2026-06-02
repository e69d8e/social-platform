package com.li.socialplatform.server.service;

import com.li.socialplatform.pojo.entity.Result;

public interface IAdminService {
    Result banUser(Long id);

    Result getBanUser(Integer pageNum, Integer pageSize);

    Result setReviewer(Long id);

    Result setUser(Long id);

    Result searchBanUser(String keyword, Integer pageNum, Integer pageSize);

    Result getDashboardStats(Integer days);
}
