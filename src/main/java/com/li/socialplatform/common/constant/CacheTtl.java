package com.li.socialplatform.common.constant;

import java.util.concurrent.TimeUnit;

/**
 * 统一的 Redis 缓存 TTL 定义，所有缓存过期时间只允许在此处声明
 *
 * @author e69d8e
 * @since 2026/09/30
 */
public final class CacheTtl {

    /** 业务数据缓存默认时长：7 天 */
    public static final long DEFAULT_DAYS = 7;
    public static final TimeUnit DEFAULT_UNIT = TimeUnit.DAYS;

    /** 分类列表缓存：10 分钟 */
    public static final long CATEGORY_MINUTES = 10;

    /** 用户角色缓存：30 分钟 */
    public static final long USER_ROLE_MINUTES = 30;

    /** 滑块验证码 / verifyToken 有效期：5 分钟 */
    public static final long CAPTCHA_SECONDS = 300;

    /** 帖子浏览量去重冷却：20 秒 */
    public static final long VIEW_COOLDOWN_SECONDS = 20;

    /** 空结果防穿透标记时长：10 分钟（DB 查询结果为空时的占位，短于业务缓存） */
    public static final long EMPTY_MARKER_MINUTES = 10;

    private CacheTtl() {
    }
}
