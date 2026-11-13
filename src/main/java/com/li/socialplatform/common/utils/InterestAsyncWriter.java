package com.li.socialplatform.common.utils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.socialplatform.pojo.entity.UserInterestScore;
import com.li.socialplatform.pojo.entity.UserTagInterest;
import com.li.socialplatform.server.mapper.UserInterestScoreMapper;
import com.li.socialplatform.server.mapper.UserTagInterestMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 异步持久化用户分类兴趣与标签兴趣评分（含最近更新时间，用于时间衰减）
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InterestAsyncWriter {

    private final UserInterestScoreMapper userInterestScoreMapper;
    private final UserTagInterestMapper userTagInterestMapper;

    @Async("mvcTaskExecutor")
    public void saveCategoryScore(Long userId, Integer categoryId, Integer score) {
        try {
            UserInterestScore existing = userInterestScoreMapper.selectOne(
                    new LambdaQueryWrapper<UserInterestScore>()
                            .eq(UserInterestScore::getUserId, userId)
                            .eq(UserInterestScore::getCategoryId, categoryId)
                            .last("LIMIT 1"));
            LocalDateTime now = LocalDateTime.now();
            if (existing == null) {
                UserInterestScore row = new UserInterestScore(userId, categoryId, score);
                row.setUpdateTime(now);
                try {
                    userInterestScoreMapper.insert(row);
                } catch (DuplicateKeyException dup) {
                    UserInterestScore retry = userInterestScoreMapper.selectOne(
                            new LambdaQueryWrapper<UserInterestScore>()
                                    .eq(UserInterestScore::getUserId, userId)
                                    .eq(UserInterestScore::getCategoryId, categoryId)
                                    .last("LIMIT 1"));
                    if (retry != null) {
                        retry.setScore(score);
                        retry.setUpdateTime(now);
                        userInterestScoreMapper.updateById(retry);
                    }
                }
            } else {
                existing.setScore(score);
                existing.setUpdateTime(now);
                userInterestScoreMapper.updateById(existing);
            }
        } catch (Exception e) {
            log.error("异步保存分类兴趣分失败: userId={}, categoryId={}, error={}", userId, categoryId, e.getMessage());
        }
    }

    @Async("mvcTaskExecutor")
    public void saveTagScore(Long userId, Long tagId, Integer score) {
        try {
            UserTagInterest existing = userTagInterestMapper.selectOne(
                    new LambdaQueryWrapper<UserTagInterest>()
                            .eq(UserTagInterest::getUserId, userId)
                            .eq(UserTagInterest::getTagId, tagId)
                            .last("LIMIT 1"));
            LocalDateTime now = LocalDateTime.now();
            if (existing == null) {
                UserTagInterest row = new UserTagInterest(userId, tagId, score);
                row.setUpdateTime(now);
                try {
                    userTagInterestMapper.insert(row);
                } catch (DuplicateKeyException dup) {
                    UserTagInterest retry = userTagInterestMapper.selectOne(
                            new LambdaQueryWrapper<UserTagInterest>()
                                    .eq(UserTagInterest::getUserId, userId)
                                    .eq(UserTagInterest::getTagId, tagId)
                                    .last("LIMIT 1"));
                    if (retry != null) {
                        retry.setScore(score);
                        retry.setUpdateTime(now);
                        userTagInterestMapper.updateById(retry);
                    }
                }
            } else {
                existing.setScore(score);
                existing.setUpdateTime(now);
                userTagInterestMapper.updateById(existing);
            }
        } catch (Exception e) {
            log.error("异步保存标签兴趣分失败: userId={}, tagId={}, error={}", userId, tagId, e.getMessage());
        }
    }
}
