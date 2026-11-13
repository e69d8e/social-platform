package com.li.socialplatform.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.li.socialplatform.pojo.dto.RecommendFeedbackDTO;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.pojo.entity.UserRecommendFeedback;
import com.li.socialplatform.recommend.FeedbackSets;

public interface IRecommendFeedbackService extends IService<UserRecommendFeedback> {

    Result submitFeedback(RecommendFeedbackDTO dto);

    Result removeFeedback(String targetType, Long targetId);

    /**
     * 读取用户全部推荐负反馈集合（Redis Cache-Aside）
     */
    FeedbackSets getFeedbackSets(Long userId);
}
