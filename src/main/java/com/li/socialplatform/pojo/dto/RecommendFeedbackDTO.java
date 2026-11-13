package com.li.socialplatform.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 推荐负反馈请求 DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecommendFeedbackDTO implements Serializable {

    /**
     * 反馈维度：POST / CATEGORY / TAG / AUTHOR
     */
    @NotBlank(message = "反馈维度不能为空")
    private String targetType;

    /**
     * 目标 ID（帖子ID / 分类ID / 标签ID / 作者ID）
     */
    @NotNull(message = "目标ID不能为空")
    private Long targetId;

    @Size(max = 64, message = "反馈原因过长")
    private String reason;
}
