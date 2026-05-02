package com.li.socialplatform.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.socialplatform.common.constant.AuthorityConstant;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.common.constant.MessageConstant;
import com.li.socialplatform.common.properties.SystemConstants;
import com.li.socialplatform.common.utils.UserIdUtil;
import com.li.socialplatform.mapper.CategoryMapper;
import com.li.socialplatform.mapper.PostImageMapper;
import com.li.socialplatform.mapper.PostMapper;
import com.li.socialplatform.mapper.UserMapper;
import com.li.socialplatform.pojo.dto.PostDTO;
import com.li.socialplatform.pojo.entity.*;
import com.li.socialplatform.pojo.vo.PostDetailVO;
import com.li.socialplatform.pojo.vo.PostVO;
import com.li.socialplatform.service.IPostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author e69d8e
 * @since 2025/12/9 14:22
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PostServiceImpl extends ServiceImpl<PostMapper, Post> implements IPostService {

    private final PostMapper postMapper;
    private final PostImageMapper postImageMapper;
    private final CategoryMapper categoryMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SystemConstants systemConstants;
    private final UserIdUtil userIdUtil;
    private final ElasticsearchOperations elasticsearchOperations;

    // 获取当前登录用户的用户名
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }

    private final UserMapper userMapper;

    @Override
    public Result publishPost(PostDTO postDTO) {
        // 获取当前登录用户id
        Long id = userIdUtil.getUserId();
        if (postDTO.getContent() == null || postDTO.getContent().isEmpty()) {
            return Result.error(MessageConstant.CONTENT_IS_NULL);
        }
        Post post = new Post();
        post.setUserId(id);
        String title = postDTO.getTitle();
        // 限制帖子标题长度
        if (title.length() > Integer.parseInt(systemConstants.titleMaxLength)) {
            return Result.error(MessageConstant.TITLE_TOO_LONG);
        }
        if (title.isEmpty()) {
            return Result.error(MessageConstant.TITLE_IS_NULL);
        }
        post.setTitle(title);
        String content = postDTO.getContent();
        // 限制帖子内容长度
        if (content.length() > Integer.parseInt(systemConstants.contentMaxLength)) {
            return Result.error(MessageConstant.CONTENT_TOO_LONG);
        }
        if (content.isEmpty()) {
            return Result.error(MessageConstant.CONTENT_IS_NULL);
        }
        post.setContent(content);
        post.setCover(postDTO.getCover());
        post.setCategoryId(postDTO.getCategoryId() == null ? 1 : postDTO.getCategoryId());
        postMapper.insert(post);
        log.info("用户 {} 发表了帖子 {}", id, post.getId());
        long time = System.currentTimeMillis();
        // 将帖子添加到缓存中
        redisTemplate.opsForZSet().add(KeyConstant.POST_LIST_KEY, post.getId(), time);
        redisTemplate.opsForZSet().add(KeyConstant.POST_KEY + id, post.getId(), time);
        // 查询所有粉丝
        List<Long> fanIds = getFanIds(id);
        // 将帖子添加到粉丝缓存
        fanIds.forEach(fanId -> redisTemplate.opsForZSet().add(KeyConstant.POST_LIST_KEY + fanId, post.getId(), time));
        // 添加帖子存到ES中
        post.setCount(0);
        post.setEnabled(true);
        elasticsearchOperations.save(post);
        return Result.ok(MessageConstant.PUBLISH_SUCCESS, "");
    }

    @Override
    public Result getPost(Long id) {
        Post post = postMapper.selectById(id);
        if (post == null) {
            return Result.error(MessageConstant.POST_NOT_EXIST);
        }
        // 获取当前用户
        User loginUser = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, getCurrentUsername()));
        if (loginUser != null) {
            if (!post.getEnabled() && !loginUser.getAuthorityId().equals(AuthorityConstant.REVIEWER)) {
                return Result.error(MessageConstant.POST_NOT_EXIST);
            }
        } else {
            if (!post.getEnabled()) {
                return Result.error(MessageConstant.POST_NOT_EXIST);
            }
        }
        User user = userMapper.selectById(post.getUserId());
        if (user == null) {
            return Result.error(MessageConstant.USER_NOT_FOUND);
        }
        // 查询是否点过赞
        Long userId = userIdUtil.getUserId();
        PostDetailVO postDetailVO = BeanUtil.copyProperties(post, PostDetailVO.class);
        postDetailVO.setCategory(categoryMapper.selectById(post.getCategoryId()).getName());
        postDetailVO.setLiked(
                userId != null && Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(KeyConstant.LIKE_KEY + id, userId)));
        Integer count = (Integer) redisTemplate.opsForValue().get(KeyConstant.LIKE_COUNT + id);
        postDetailVO.setCount(count == null ? 0 : count);
        postDetailVO.setAvatar(user.getAvatar());
        postDetailVO.setNickname(user.getNickname());
        postDetailVO.setCover(post.getCover());
        if (userId == null) {
            postDetailVO.setFollowed(false);
        } else {
            Double score = redisTemplate.opsForZSet().score(KeyConstant.Follow_LIST_KEY + userId, user.getId());
            postDetailVO.setFollowed(score != null);
        }
        return Result.ok(postDetailVO);
    }

    @Override
    public Result listPosts(Long lastId, Integer offset) {
        Set<ZSetOperations.TypedTuple<Object>> typedTuples = redisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(KeyConstant.POST_LIST_KEY,
                        0, lastId, offset, Long.parseLong(systemConstants.defaultPageSize));
        // 获取当前用户
        Long userId = userIdUtil.getUserId();
        if (userId == null) {
            return Result.ok(getScrollResult(typedTuples, null));
        }
        return Result.ok(getScrollResult(typedTuples, userId));
    }

    @Override
    public Result listFollowPosts(Long lastId, Integer offset) {
        // 获取当前用户
        Long userId = userIdUtil.getUserId();
        Set<ZSetOperations.TypedTuple<Object>> typedTuples = redisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(KeyConstant.POST_LIST_KEY + userId,
                        0, lastId, offset, Long.parseLong(systemConstants.defaultPageSize));
        return Result.ok(getScrollResult(typedTuples, userId));
    }

    @Override
    public Result userListPosts(Long id, Integer pageNum, Integer pageSize) {
        IPage<Post> page = new Page<>(pageNum, pageSize);
        IPage<Post> postIPage = postMapper.selectPage(page, new LambdaQueryWrapper<Post>().eq(Post::getUserId, id));
        List<Post> records = postIPage.getRecords();
        List<PostVO> postVOS = new ArrayList<>();
        Long userId = userIdUtil.getUserId();
        for (Post record : records) {
            PostVO postVO = BeanUtil.copyProperties(record, PostVO.class);
            Integer count = (Integer) redisTemplate.opsForValue().get(KeyConstant.LIKE_COUNT + record.getId());
            postVO.setCount(count == null ? 0 : count);
            if (userId == null) {
                postVO.setLiked(false);
            } else {
                postVO.setLiked(redisTemplate.opsForSet()
                        .isMember(KeyConstant.LIKE_KEY + record.getId(), userId));
            }
            postVOS.add(postVO);
        }
        return Result.ok(postVOS, postIPage.getTotal());
    }

    @Override
    public Result deletePost(Long id) {
        // 当前登录用户id
        Long userId = userIdUtil.getUserId();
        // 判断当前用户是不是帖子的拥有者
        Post post = postMapper.selectOne(new LambdaQueryWrapper<Post>().eq(Post::getUserId, userId).eq(Post::getId, id));
        if (post == null) {
            return Result.error(MessageConstant.POST_NOT_EXIST);
        }
        // 删除帖子
        postMapper.deleteById(id);
        // 删除帖子图片
        postImageMapper.delete(new LambdaQueryWrapper<PostImage>().eq(PostImage::getPostId, id));
        List<Long> fanIds = getFanIds(userId);
        // 将他的粉丝的缓存中的数据清除
        for (Long fanId : fanIds) {
            redisTemplate.opsForZSet().remove(KeyConstant.POST_LIST_KEY + fanId, id);
        }
        // 首页帖子删除
        redisTemplate.opsForZSet().remove(KeyConstant.POST_LIST_KEY, id);
        // 我的帖子删除
        redisTemplate.opsForZSet().remove(KeyConstant.POST_KEY + userId, id);
        return Result.ok(MessageConstant.DELETE_SUCCESS, "");
    }

    private List<Long> getFanIds(Long userId) {
        Set<Object> fans = redisTemplate.opsForZSet().range(KeyConstant.FANS_LIST_KEY + userId, 0, -1);
        if (fans == null || fans.isEmpty()) {
            return List.of();
        }
        // 解析
        return fans.stream().map(fan -> Long.valueOf(fan.toString())).toList();
    }

    private ScrollResult<PostVO> getScrollResult(Set<ZSetOperations.TypedTuple<Object>> typedTuples, Long userId) {
        if (typedTuples == null || typedTuples.isEmpty()) {
            ScrollResult<PostVO> objectScrollResult = new ScrollResult<>();
            objectScrollResult.setList(new ArrayList<>());
            objectScrollResult.setMinTime(0L);
            objectScrollResult.setOffset(1);
            return objectScrollResult;
        }
        // 获取 id
        List<Object> collect = typedTuples.stream()
                .map(ZSetOperations.TypedTuple::getValue).toList();
        // 解析id
        List<Long> ids = collect.stream().map(id -> Long.parseLong(id.toString())).toList();
        log.info("获取帖子 {}", ids);
        // 获取 score
        List<Double> scores = typedTuples.stream()
                .map(ZSetOperations.TypedTuple::getScore).toList();
        List<PostVO> postVOS = new ArrayList<>();
        for (Long id : ids) {
            Post post = postMapper.selectById(id);
            if (post == null) {
                continue;
            }
            if (!post.getEnabled()) {
                continue;
            }
            PostVO postVO = BeanUtil.copyProperties(post, PostVO.class);
            Integer count = (Integer) redisTemplate.opsForValue().get(KeyConstant.LIKE_COUNT + id);
            postVO.setCount(count == null ? 0 : count);
            if (userId == null) {
                postVO.setLiked(false);
            } else {
                postVO.setLiked(Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(KeyConstant.LIKE_KEY + id, userId)));
            }
            postVOS.add(postVO);
        }
        int nweOffset = 1;
        double score = scores.getFirst();
        for (int i = 1; i < scores.size(); i++) {
            if (score == scores.get(i)) {
                nweOffset++;
            } else {
                nweOffset = 1;
            }
            score = scores.get(i);
        }
        ScrollResult<PostVO> postVOScrollResult = new ScrollResult<>();
        postVOScrollResult.setList(postVOS);
        postVOScrollResult.setOffset(nweOffset);
        postVOScrollResult.setMinTime(scores.getLast().longValue());
        return postVOScrollResult;
    }
}
