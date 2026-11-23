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
    public static final String PROFILE_VISIT_KEY = "profile-visit:";
    public static final String Follow_LIST_KEY = "follow-list:";
    public static final String POST_KEY = "post:";
    public static final String LIKE_COUNT = "like-count:";
    public static final String CATEGORY_LIST_KEY = "category-list:";
    public static final String CATEGORY_VO_LIST_KEY = "category-vo-list:";
    public static final String TAG_HOT_KEY = "tag-hot:";
    public static final String USER_TAG_INTEREST_KEY = "user-tag-interests:";
    public static final String USER_TAG_INTEREST_TS_KEY = "user-tag-interests-ts:";
    public static final String USER_TAG_INTEREST_LOADED_KEY = "tag-interest-loaded:";
    public static final String USER_INTEREST_TS_KEY = "user-interests-ts:";
    public static final String INTEREST_VIEW_COOLDOWN_KEY = "interest-view-cd:";
    public static final String RECOMMEND_FEEDBACK_KEY = "rec-feedback:";
    public static final String RECOMMEND_SEEN_KEY = "rec-seen:";
    public static final String RECOMMEND_SNAPSHOT_KEY = "rec-snapshot:";
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
    /** 互动通知未读数 */
    public static final String NOTIFICATION_UNREAD_KEY = "notification-unread:";

    // ==================== 用户黑名单（用户级拉黑，区别于管理员 ban_record） ====================

    /** 我拉黑的人（Set） */
    public static final String USER_BLACKLIST_KEY = "blacklist:";
    /** 拉黑我的人（Set，用于对称隐藏） */
    public static final String USER_BLOCKED_BY_KEY = "blocked-by:";

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
    /** 用户黑名单已从 DB 加载标记（Set 无法表达空集合，需独立标记） */
    public static final String BLACKLIST_LOADED_KEY = "blacklist-loaded:";
}