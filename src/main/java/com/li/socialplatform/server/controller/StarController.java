package com.li.socialplatform.server.controller;

import com.li.socialplatform.common.annotation.RateLimit;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.server.service.IStarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @author e69d8e
 * @since 2026/10/28
 */
@RestController
@RequestMapping("/star")
@Tag(name = "收藏", description = "帖子收藏/取消收藏（切换操作）")
@RequiredArgsConstructor
@Validated
public class StarController {

    private final IStarService starService;

    @PutMapping("/{postId}")
    @Operation(summary = "收藏/取消收藏", description = "对帖子进行收藏或取消收藏（切换操作）")
    @RateLimit()
    public Result star(
            @Parameter(description = "帖子ID") @PathVariable Long postId) {
        return starService.star(postId);
    }

    @GetMapping("/list")
    @Operation(summary = "我的收藏列表", description = "分页获取当前用户收藏的帖子")
    public Result listMyStars(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "8") @Min(1) @Max(100) Integer pageSize) {
        return starService.listMyStars(pageNum, pageSize);
    }
}
