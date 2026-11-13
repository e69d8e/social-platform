package com.li.socialplatform.server.controller;

import com.li.socialplatform.pojo.dto.RecommendFeedbackDTO;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.server.service.IRecommendFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/recommend")
@Tag(name = "推荐反馈", description = "首页推荐负反馈（不感兴趣/屏蔽分类/标签/作者与撤销）")
@RequiredArgsConstructor
public class RecommendController {

    private final IRecommendFeedbackService recommendFeedbackService;

    @PostMapping("/feedback")
    @Operation(summary = "提交推荐负反馈", description = "对帖子、分类、标签或作者提交不感兴趣反馈")
    public Result submitFeedback(@Valid @RequestBody RecommendFeedbackDTO dto) {
        return recommendFeedbackService.submitFeedback(dto);
    }

    @DeleteMapping("/feedback")
    @Operation(summary = "撤销推荐负反馈", description = "撤销此前对指定目标的不感兴趣反馈")
    public Result removeFeedback(
            @Parameter(description = "目标类型: POST/CATEGORY/TAG/AUTHOR") @RequestParam String targetType,
            @Parameter(description = "目标ID") @RequestParam Long targetId) {
        return recommendFeedbackService.removeFeedback(targetType, targetId);
    }
}
