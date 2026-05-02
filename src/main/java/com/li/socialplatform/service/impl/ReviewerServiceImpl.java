package com.li.socialplatform.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.common.constant.MessageConstant;
import com.li.socialplatform.common.utils.UserIdUtil;
import com.li.socialplatform.mapper.CommentMapper;
import com.li.socialplatform.mapper.PostMapper;
import com.li.socialplatform.pojo.entity.Post;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.pojo.vo.PostVO;
import com.li.socialplatform.service.IReviewerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author e69d8e
 * @since 2025/12/10 14:01
 */
@Service
@RequiredArgsConstructor
public class ReviewerServiceImpl implements IReviewerService {

    private final PostMapper postMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserIdUtil userIdUtil;
    private final CommentMapper commentMapper;

    @Override
    public Result banPost(Long id) {
        Post post = postMapper.selectById(id);
        if (post == null) {
            return Result.error(MessageConstant.POST_NOT_EXIST);
        }
        post.setEnabled(!post.getEnabled());
        // 将封禁的帖子加入缓存中
        if (!post.getEnabled()) {
            redisTemplate.opsForZSet().add(KeyConstant.BAN_POST_KEY + userIdUtil.getUserId(), id, System.currentTimeMillis());
        } else {
            redisTemplate.opsForZSet().remove(KeyConstant.BAN_POST_KEY + userIdUtil.getUserId(), id);
        }
        // 更新数据库
        return postMapper.updateById(post) > 0 ? Result.ok(MessageConstant.BAN_SUCCESS, "") : Result.error(MessageConstant.BAN_FAIL);
    }

    @Override
    public Result listBanPost(Integer pageNum, Integer pageSize) {
        long start = ((long) (pageNum - 1) * pageSize);
        long end = start + pageSize - 1;
        Long userId = userIdUtil.getUserId();
        Long total = redisTemplate.opsForZSet().size(KeyConstant.BAN_POST_KEY + userId);
        if (total == null) {
            return Result.ok(List.of(), 0L);
        }
        if (start > total) {
            return Result.ok(List.of(), 0L);
        }
        if (end > total) {
            end = total - 1;
        }
        Set<Object> members = redisTemplate.opsForZSet().range(KeyConstant.BAN_POST_KEY + userId, start, end);
        if (members == null || members.isEmpty()) {
            return Result.ok(List.of(), 0L);
        }
        List<Long> ids = members.stream().map(member -> Long.valueOf(member.toString())).toList();
        List<PostVO> postVOS = new ArrayList<>();
        for (Long id : ids) {
            Post post = postMapper.selectById(id);
            PostVO postVO = BeanUtil.copyProperties(post, PostVO.class);
            postVO.setCount((Integer) redisTemplate.opsForValue().get(KeyConstant.LIKE_COUNT + id));
            if (userId != null) {
                postVO.setLiked(redisTemplate.opsForSet().isMember(KeyConstant.LIKE_KEY + id, userId));
            } else {
                postVO.setLiked(false);
            }
            postVO.setEnabled(false);
            postVOS.add(postVO);
        }
        return Result.ok(postVOS, total);
    }

    @Override
    public Result deleteComment(Long id, Long postId) {
        redisTemplate.opsForZSet().remove(KeyConstant.COMMENT_KEY + postId, id);
        commentMapper.deleteById(id);
        return Result.ok(MessageConstant.DELETE_SUCCESS, "");
    }

}
