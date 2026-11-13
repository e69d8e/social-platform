package com.li.socialplatform.server.controller;

import com.li.socialplatform.common.annotation.RateLimit;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.server.service.IBlacklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户黑名单（用户级拉黑）
 *
 * <p>拉黑是双向的：双方互不可见对方帖子、互不可私信、互不可点赞/收藏/评论/关注。
 * 管理员/审核员的治理接口（/admin/**、/reviewer/**）不受影响。</p>
 *
 * @author e69d8e
 */
@RestController
@RequestMapping("/blacklist")
@Tag(name = "黑名单", description = "用户级拉黑：双向隐藏内容并禁止私信")
@RequiredArgsConstructor
@Validated
public class BlacklistController {

    private final IBlacklistService blacklistService;

    @PostMapping("/{userId}")
    @Operation(summary = "拉黑用户", description = "拉黑指定用户（幂等）。拉黑后双方互不可见内容、互不可私信，并自动解除关注关系")
    @RateLimit()
    public Result block(
            @Parameter(description = "被拉黑用户ID") @PathVariable Long userId) {
        return blacklistService.block(userId);
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "解除拉黑", description = "解除对指定用户的拉黑（幂等）。内容可见性与私信立即恢复，关注关系不会自动恢复")
    @RateLimit()
    public Result unblock(
            @Parameter(description = "被拉黑用户ID") @PathVariable Long userId) {
        return blacklistService.unblock(userId);
    }

    @GetMapping
    @Operation(summary = "我的黑名单", description = "分页获取当前用户的黑名单，按拉黑时间倒序")
    public Result list(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer pageSize) {
        return blacklistService.list(pageNum, pageSize);
    }
}
