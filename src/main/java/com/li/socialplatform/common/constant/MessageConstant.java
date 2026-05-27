package com.li.socialplatform.common.constant;

/**
 * @author e69d8e
 * @since 2025/12/8 16:15
 */
public class MessageConstant {
    public static final String USER_IS_EMPTY  = "用户名和密码不能为空";
    public static final String USERNAME_ALREADY_EXISTS = "该用户已经存在";
    public static final String USERNAME_FORMAT_ERROR = "用户名格式错误";
    public static final String PASSWORD_FORMAT_ERROR = "密码格式错误";
    public static final String USER_PASSWORD_ERROR = "密码错误";

    // 异常
    public static final String EXCEPTION = "服务器异常";
    public static final String BizException_CODE = "1002";
    public static final String ID_IS_NULL = "ID为空";
    public static final String USER_IS_FOLLOWED = "用户已经关注了";
    public static final String USER_NOT_EXIST = "用户不存在";
    public static final String USER_CANNOT_FOLLOW_SELF = "无法关注自己";
    public static final String USER_NOT_FOLLOWED = "用户未关注";
    public static final String USER_FANS_PRIVATE = "该用户粉丝列表为私密状态";
    public static final String USER_FOLLOW_PRIVATE = "该用户关注列表为私密状态";
    public static final String CONTENT_IS_NULL = "内容不能为空";
    public static final String USER_NOT_FOUND = "用户不存在";
    public static final String POST_NOT_EXIST = "该帖子不存在";
    public static final String TITLE_IS_NULL = "标题不能为空";
    public static final String DELETE_SUCCESS = "删除成功";
    public static final String PUBLISH_SUCCESS = "发布成功";
    public static final String BAN_SUCCESS = "封禁/解封成功";
    public static final String BAN_FAIL = "封禁/解封失败";
    public static final String SET_SUCCESS = "设置成功";
    public static final String SET_FAIL = "设置失败";
    public static final String FOLLOW_SUCCESS = "关注成功";
    public static final String UN_FOLLOW_SUCCESS = "取消成功";
    public static final String REGISTER_SUCCESS = "注册成功";
    public static final String TITLE_TOO_LONG = "标题过长";
    public static final String CONTENT_TOO_LONG = "内容过长";
    public static final String USER_INFO_ERROR = "格式异常";
    public static final String NICKNAME_ERROR = "昵称不能为空或者长度不能超过16";
    public static final String BIO_ERROR = "简介长度不能超过50";
    public static final String ADD_COMMENT_SUCCESS = "评论成功";
    public static final String LIKE_SUCCESS = "点赞/取消成功";
    public static final String UPDATE_SUCCESS = "更新成功";
    public static final String USER_NOT_LOGIN = "用户未登录";
    public static final String USER_NOT_AUTHORIZED = "用户未授权";
    public static final String USER_LOGIN_SUCCESS = "登录成功";
    public static final String USER_LOGOUT_SUCCESS = "注销成功";
    public static final String USER_NOT_ENABLED = "用户被封禁";
    public static final String SESSION_NOT_EXIST = "会话不存在";

    // 私信
    public static final String RECEIVER_NOT_EXIST = "接收方用户不存在";
    public static final String CANNOT_MESSAGE_SELF = "不能给自己发送私信";
    public static final String MESSAGE_CONTENT_EMPTY = "消息内容不能为空";
    public static final String CONVERSATION_NOT_EXIST = "会话不存在";
    public static final String NOT_CONVERSATION_PARTICIPANT = "您不是该会话的参与者";
    public static final String MESSAGE_SEND_SUCCESS = "消息发送成功";
}
