package com.li.socialplatform.recommend;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 缓存于 Redis 推荐流快照中的紧凑条目（仅保存帖子 ID 与推荐理由）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RankedPostEntry implements Serializable {
    private Long postId;
    private String reason;
}
