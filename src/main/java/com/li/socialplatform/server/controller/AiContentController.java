package com.li.socialplatform.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.socialplatform.assistant.ContentAssistant;
import com.li.socialplatform.common.annotation.RateLimit;
import com.li.socialplatform.pojo.entity.Comment;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.server.mapper.CommentMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 内容创作助手
 *
 * @author e69d8e
 */
@RestController
@RequestMapping("/ai/content")
@Tag(name = "AI内容助手", description = "AI辅助标题生成、内容润色、评论总结")
@RequiredArgsConstructor
public class AiContentController {

    private final ContentAssistant contentAssistant;
    private final CommentMapper commentMapper;

    @PostMapping("/titles")
    @Operation(summary = "AI生成标题", description = "根据帖子内容生成3个标题建议")
    @RateLimit(maxRequests = 10, timeWindow = 60)
    public Result generateTitles(@RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            return Result.error("内容不能为空");
        }
        // 截取前2000字发给AI
        String trimmed = content.length() > 2000 ? content.substring(0, 2000) : content;
        String result = contentAssistant.generateTitles(trimmed);
        List<String> titles = result.lines()
                .filter(l -> !l.isBlank())
                .map(String::trim)
                .limit(3)
                .collect(Collectors.toList());
        return Result.ok(titles);
    }

    @PostMapping("/polish")
    @Operation(summary = "AI润色内容", description = "润色帖子内容使其更流畅")
    @RateLimit(maxRequests = 10, timeWindow = 60)
    public Result polishContent(@RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            return Result.error("内容不能为空");
        }
        String trimmed = content.length() > 3000 ? content.substring(0, 3000) : content;
        String polished = contentAssistant.polishContent(trimmed);
        return Result.ok(Map.of("polished", polished));
    }

    @GetMapping("/summarize/{postId}")
    @Operation(summary = "AI总结评论", description = "总结帖子评论区的讨论要点")
    @RateLimit(maxRequests = 5, timeWindow = 60)
    public Result summarizeComments(@PathVariable Long postId) {
        List<Comment> comments = commentMapper.selectList(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getPostId, postId)
                        .orderByDesc(Comment::getCreateTime)
                        .last("LIMIT 30"));
        if (comments.isEmpty()) {
            return Result.error("暂无评论");
        }
        String commentText = comments.stream()
                .map(Comment::getContent)
                .collect(Collectors.joining("\n"));
        String summary = contentAssistant.summarizeComments(commentText);
        return Result.ok(Map.of("summary", summary));
    }
}
