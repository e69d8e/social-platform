package com.li.socialplatform.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.pojo.entity.UserBlacklist;

/**
 * 用户黑名单服务（用户级拉黑，区别于管理员 ban_record）
 *
 * @author e69d8e
 */
public interface IBlacklistService extends IService<UserBlacklist> {

    /** 拉黑指定用户：幂等；自动双向解除关注 */
    Result block(Long targetUserId);

    /** 解除拉黑：幂等 */
    Result unblock(Long targetUserId);

    /** 我的黑名单（按拉黑时间倒序分页） */
    Result list(Integer pageNum, Integer pageSize);
}
