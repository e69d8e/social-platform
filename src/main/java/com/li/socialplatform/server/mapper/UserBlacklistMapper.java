package com.li.socialplatform.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.li.socialplatform.pojo.entity.UserBlacklist;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户黑名单 Mapper
 *
 * @author e69d8e
 */
@Mapper
public interface UserBlacklistMapper extends BaseMapper<UserBlacklist> {
}
