package com.li.socialplatform.common.constant;

/**
 * @author e69d8e
 * @since 2025/12/8 23:12
 */
public class KeyConstant {
    public static final String FOLLOW_COUNT_KEY = "follow-count:";
    public static final String FANS_LIST_KEY = "fans-list:";
    public static final String POST_LIST_KEY = "post-list:";
    public static final String COMMENT_KEY = "comment:";
    public static final String LIKE_KEY = "like:";
    public static final String SIGN_IN_KEY = "sign-in:";
    public static final String Follow_LIST_KEY = "follow-list:";
    public static final String POST_KEY = "post:";
    public static final String LIKE_COUNT = "like-count:";
    public static final String CATEGORY_LIST_KEY = "category-list:";
    public static final String BAN_POST_KEY = "ban-post-list:";
    public static final String BAN_USER_KEY = "ban-use-list:";
    public static final String FRIEND_LIST_KEY = "friend-list:";
    public static final String REFRESH_KEY = "refresh:";
    public static final String POST_ID_KEY = "post:";
    public static final String USER_INTEREST_SCORE_KEY = "user-interests:";
    public static final String TOKEN_BLACKLIST_KEY = "token-blacklist:";
    public static final String POST_VIEW_COUNT = "post-view-count:";
    public static final String RATE_LIMIT_KEY = "rate-limit:";
    public static final String POST_VIEW_COOLDOWN = "post-view-cooldown:";
    public static final String HOME_POST_LIST_KEY = "home-post-list:";
    public static final String USER_INBOX_KEY = "user-inbox:";
    public static final String SLIDE_CAPTCHA_KEY = "slide:captcha:";
    public static final String SLIDE_VERIFY_KEY = "slide:verify:";
    public static final String USER_ROLE_KEY = "user-role:";

    // ==================== 空结果防穿透标记（业务缓存查空时写入，短 TTL） ====================

    /** 用户关注/粉丝数据已从 DB 加载（ZSet 无法表达空集合，需独立标记） */
    public static final String FOLLOW_LOADED_KEY = "follow-loaded:";
    /** 全局封禁用户列表为空标记 */
    public static final String BAN_USER_EMPTY_KEY = "ban-user-empty:";
    /** 审核员封禁帖子列表为空标记 */
    public static final String BAN_POST_EMPTY_KEY = "ban-post-empty:";
    /** 首页帖子列表为空标记 */
    public static final String HOME_POST_EMPTY_KEY = "home-post-empty:";
    /** 用户兴趣分为空标记 */
    public static final String INTEREST_LOADED_KEY = "interest-loaded:";
}