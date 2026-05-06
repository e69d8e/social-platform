package com.li.socialplatform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.common.constant.MessageConstant;
import com.li.socialplatform.common.utils.UserIdUtil;
import com.li.socialplatform.mapper.LikeMapper;
import com.li.socialplatform.mapper.PostMapper;
import com.li.socialplatform.mapper.UserMapper;
import com.li.socialplatform.pojo.entity.LikeRecord;
import com.li.socialplatform.pojo.entity.Message;
import com.li.socialplatform.pojo.entity.Post;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.service.ILikeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author e69d8e
 * @since 2025/12/9 21:30
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class LikeServiceImpl extends ServiceImpl<LikeMapper, LikeRecord> implements ILikeService {

    private final LikeMapper likeMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserIdUtil userIdUtil;
    private final ElasticsearchOperations elasticsearchOperations;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final UserMapper userMapper;
    private final PostMapper postMapper;

    @Override
    public Result like(Long postId) {
        Long userId = userIdUtil.getUserId();
        String key = KeyConstant.LIKE_KEY + postId;
        Boolean member = redisTemplate.opsForSet().isMember(key, userId);
        if (Boolean.TRUE.equals(member)) {
            // 点赞数-1
            Long increment = redisTemplate.opsForValue().increment(KeyConstant.LIKE_COUNT + postId, -1);
            likeMapper.delete(new LambdaQueryWrapper<LikeRecord>().eq(LikeRecord::getPostId, postId).eq(LikeRecord::getUserId, userId));
            redisTemplate.opsForSet().remove(key, userId);
            // 更新 Elasticsearch
            Post post = elasticsearchOperations.get(postId.toString(), Post.class);
            if (post != null) {
                if (increment != null) {
                    post.setCount(increment.intValue());
                    elasticsearchOperations.save(post);
                }
            }
        } else {
            // 点赞数+1
            Long increment = redisTemplate.opsForValue().increment(KeyConstant.LIKE_COUNT + postId, 1);
            likeMapper.insert(new LikeRecord(null, postId, userId, null));
            redisTemplate.opsForSet().add(key, userId);
            // 更新 Elasticsearch
            Post post = elasticsearchOperations.get(postId.toString(), Post.class);
            if (post != null) {
                if (increment != null) {
                    post.setCount(increment.intValue());
                    elasticsearchOperations.save(post);
                }
            }
            // 通知
            if (post != null) {
                String username = userMapper.selectById(post.getUserId()).getUsername();
                String title = postMapper.selectById(postId).getTitle();
                Message message = new Message(postId, "有人点赞了你的帖子", title);
                simpMessagingTemplate.convertAndSendToUser(username, "/queue/msg", message);
            }
        }
        return Result.ok(MessageConstant.LIKE_SUCCESS, "");
    }
}