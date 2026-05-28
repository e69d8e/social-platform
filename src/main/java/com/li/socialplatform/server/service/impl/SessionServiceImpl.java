package com.li.socialplatform.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.li.socialplatform.bean.Messages;
import com.li.socialplatform.common.constant.MessageConstant;
import com.li.socialplatform.common.utils.UserIdUtil;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.pojo.entity.Session;
import com.li.socialplatform.server.mapper.SessionMapper;
import com.li.socialplatform.server.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private final SessionMapper sessionMapper;
    private final MongoTemplate mongoTemplate;
    private final UserIdUtil userIdUtil;

    @Override
    public Result create() {
        return Result.ok(UUID.randomUUID().toString());
    }

    @Override
    public Result delete(String sessionId) {
        if (sessionMapper.deleteById(sessionId) > 0) {
            // 删除 MongoDB 中的聊天记录
            Criteria criteria = Criteria.where("memoryId").is(sessionId);
            Query query = Query.query(criteria);
            mongoTemplate.remove(query, Messages.class);
            return Result.ok();
        }
        return Result.error(MessageConstant.SESSION_NOT_EXIST);
    }

    @Override
    public Result getSessions(Integer page, Integer size) {
        log.info("获取会话");
        // 获取当前用户id
        Long userId = userIdUtil.getUserId();
        // 分页查询会话
        Page<Session> sessionPage = new Page<>(page, size);
        LambdaQueryWrapper<Session> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Session::getUserId, userId); // 条件
        Page<Session> session = sessionMapper.selectPage(sessionPage, queryWrapper);
        return Result.ok(session.getRecords(), session.getTotal());
    }

    @Override
    public Result getSession(String sessionId) {
        Criteria criteria = Criteria.where("memoryId").is(sessionId);
        Query query = Query.query(criteria);
        Messages messages = mongoTemplate.findOne(query, Messages.class);
        if (messages == null) {
            return Result.error(MessageConstant.SESSION_NOT_EXIST);
        }
        String content = messages.getContent();
        return Result.ok(content);
    }
}
