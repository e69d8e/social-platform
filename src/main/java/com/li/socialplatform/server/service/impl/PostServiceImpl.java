package com.li.socialplatform.server.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.socialplatform.common.constant.AuthorityConstant;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.common.constant.MessageConstant;
import com.li.socialplatform.common.properties.SystemConstants;
import com.li.socialplatform.common.utils.*;
import com.li.socialplatform.pojo.dto.PostDTO;
import com.li.socialplatform.pojo.entity.*;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.pojo.vo.PostDetailVO;
import com.li.socialplatform.pojo.vo.PostVO;
import com.li.socialplatform.server.mapper.*;
import com.li.socialplatform.server.repository.PostElasticsearchRepository;
import com.li.socialplatform.server.service.IPostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionBoostMode;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author e69d8e
 * @since 2025/12/9 14:22
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PostServiceImpl extends ServiceImpl<PostMapper, Post> implements IPostService {

    private final PostMapper postMapper;
    private final HomePostMapper homePostMapper;
    private final CategoryMapper categoryMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SystemConstants systemConstants;
    private final UserIdUtil userIdUtil;
    private final PostElasticsearchRepository postElasticsearchRepository;
    private final CommentMapper commentMapper;
    private final LikeMapper likeMapper;
    private final FileMapper fileMapper;
    private final DeleteFileUtil deleteFileUtil;
    private final RedisIdUtils redisIdUtils;
    private final UserIntersetScoreUtil userIntersetScoreUtil;
    private final DataCacheUtil dataCacheUtil;
    private final ElasticsearchTemplate elasticsearchTemplate;
    private final UserInboxMapper userInboxMapper;
    private final AsyncTaskUtil asyncTaskUtil;

    private static final long HOME_POST_CACHE_TTL_DAYS = 7;

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
        User user = userMapper.selectById(id);
        if (!user.getEnabled()) {
            return Result.error(MessageConstant.USER_NOT_ENABLED);
        }
        if (postDTO.getContent() == null || postDTO.getContent().isEmpty()) {
            return Result.error(MessageConstant.CONTENT_IS_NULL);
        }
        Post post = new Post();
        post.setId(postDTO.getId());
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
        // 将帖子添加到首页帖子表
        HomePost homePost = new HomePost();
        homePost.setPostId(post.getId());
        homePost.setUserId(id);
        homePostMapper.insert(homePost);
        // 添加到首页帖子缓存
        String homePostKey = KeyConstant.HOME_POST_LIST_KEY;
        redisTemplate.opsForZSet().add(homePostKey, post.getId(), time);
        redisTemplate.expire(homePostKey, HOME_POST_CACHE_TTL_DAYS, TimeUnit.DAYS);
        // 查询所有粉丝
        List<Long> fanIds = getFanIds(id);
        // 将帖子添加到粉丝缓存
        fanIds.forEach(fanId -> redisTemplate.opsForZSet().add(KeyConstant.POST_LIST_KEY + fanId, post.getId(), time));
        // 异步写入粉丝收件箱表
        if (!fanIds.isEmpty()) {
            asyncTaskUtil.asyncInsertUserInbox(fanIds, post.getId(), id);
        }
        post.setLikeCount(0);
        post.setEnabled(true);
        // 转换为纯文本
        String text = HtmlUtils.htmlToPlainText(postDTO.getContent());
        post.setContent(text);
        // 添加到ES中
        postElasticsearchRepository.save(post);
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
        Long userId = userIdUtil.getUserId();
        if (userId != null) {
            userIntersetScoreUtil.changeScore(userId, post.getCategoryId(), 1);
        }
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
        PostDetailVO postDetailVO = BeanUtil.copyProperties(post, PostDetailVO.class);
        postDetailVO.setCategory(categoryMapper.selectById(post.getCategoryId()).getName());
        postDetailVO.setLiked(dataCacheUtil.isLiked(id, userId));
        postDetailVO.setLikeCount(dataCacheUtil.getLikeCount(id));
        postDetailVO.setAvatar(user.getAvatar());
        postDetailVO.setNickname(user.getNickname());
        postDetailVO.setCover(post.getCover());
        postDetailVO.setFollowed(dataCacheUtil.isFollowed(userId, user.getId()));
        // 读取浏览量（Redis 中待同步的增量 + DB 中已持久化的值）
        Integer viewCount = (Integer) redisTemplate.opsForValue().get(KeyConstant.POST_VIEW_COUNT + id);
        postDetailVO.setViewCount((post.getViewCount() == null ? 0 : post.getViewCount()) + (viewCount == null ? 0 : viewCount));
        return Result.ok(postDetailVO);
    }

    @Override
    public Result listPosts(Long lastId, Integer offset) {
        int pageSize = Integer.parseInt(systemConstants.defaultPageSize);
        Long userId = userIdUtil.getUserId();

        // 未登录：按时间倒序从首页帖子缓存获取
        if (userId == null) {
            Set<ZSetOperations.TypedTuple<Object>> typedTuples = getHomePostCache(lastId, offset, pageSize);
            return Result.ok(getScrollResult(typedTuples, null));
        }

        // 获取用户兴趣评分
        Map<Integer, Integer> interestScores = userIntersetScoreUtil.getUserInterestScores(userId);

        if (interestScores.isEmpty()) {
            // 无兴趣数据：按时间倒序从首页帖子缓存获取
            Set<ZSetOperations.TypedTuple<Object>> typedTuples = getHomePostCache(lastId, offset, pageSize);
            return Result.ok(getScrollResult(typedTuples, userId));
        }

        // 个性化推荐：使用 ES 按兴趣分类加权查询
        return getPersonalizedPosts(userId, interestScores, offset, pageSize);
    }

    /**
     * 获取首页帖子缓存，缓存未命中时从 home_post 表加载
     */
    private Set<ZSetOperations.TypedTuple<Object>> getHomePostCache(Long lastId, int offset, int pageSize) {
        String homePostKey = KeyConstant.HOME_POST_LIST_KEY;
        Set<ZSetOperations.TypedTuple<Object>> typedTuples = redisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(homePostKey, 0, lastId, offset, pageSize);
        if (typedTuples != null && !typedTuples.isEmpty()) {
            return typedTuples;
        }
        // 缓存未命中，从 home_post 表加载
        loadHomePostFromDB();
        return redisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(homePostKey, 0, lastId, offset, pageSize);
    }

    /**
     * 从 home_post 表加载数据到 Redis 缓存
     */
    private void loadHomePostFromDB() {
        String homePostKey = KeyConstant.HOME_POST_LIST_KEY;
        List<HomePost> homePosts = homePostMapper.selectList(
                new LambdaQueryWrapper<HomePost>().orderByDesc(HomePost::getCreateTime));
        if (homePosts.isEmpty()) {
            return;
        }
        for (HomePost hp : homePosts) {
            double score = hp.getCreateTime() != null
                    ? hp.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    : (double) System.currentTimeMillis();
            redisTemplate.opsForZSet().add(homePostKey, hp.getPostId(), score);
        }
        redisTemplate.expire(homePostKey, HOME_POST_CACHE_TTL_DAYS, TimeUnit.DAYS);
        log.info("从 home_post 表加载 {} 条首页帖子到缓存", homePosts.size());
    }

    private Result getPersonalizedPosts(Long userId, Map<Integer, Integer> interestScores, int offset, int pageSize) {
        int maxScore = interestScores.values().stream().mapToInt(Integer::intValue).max().orElse(1);

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(Query.of(q -> q.functionScore(fs -> {
                    fs.query(fq -> fq.bool(b -> {
                        b.must(m -> m.term(t -> t.field("enabled").value(true)));
                        for (Map.Entry<Integer, Integer> entry : interestScores.entrySet()) {
                            if (entry.getValue() > 0) {
                                float boost = (float) entry.getValue() / maxScore;
                                b.should(s -> s.term(t -> t.field("categoryId").value(entry.getKey()).boost(boost)));
                            }
                        }
                        b.minimumShouldMatch("0");
                        return b;
                    }));
                    fs.functions(f -> f.gauss(g -> g
                            .date(d -> d
                                    .field("createTime")
                                    .placement(p -> p
                                            .origin("now")
                                            .scale(Time.of(t -> t.time("7d")))
                                            .decay(0.5)
                                    )
                            )
                    ));
                    fs.boostMode(FunctionBoostMode.Multiply);
                    return fs;
                })))
                .withSort(Sort.by(Sort.Order.desc("_score")))
                .withPageable(PageRequest.of(offset / pageSize, pageSize))
                .build();

        SearchHits<Post> searchHits = elasticsearchTemplate.search(nativeQuery, Post.class);

        List<PostVO> postVOS = new ArrayList<>();
        long minTime = 0L;
        for (SearchHit<Post> hit : searchHits) {
            Post post = hit.getContent();
            PostVO postVO = BeanUtil.copyProperties(post, PostVO.class);
            postVO.setLikeCount(dataCacheUtil.getLikeCount(post.getId()));
            postVO.setLiked(dataCacheUtil.isLiked(post.getId(), userId));
            postVOS.add(postVO);
            if (post.getCreateTime() != null) {
                minTime = post.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            }
        }

        ScrollResult<PostVO> result = new ScrollResult<>();
        result.setList(postVOS);
        result.setMinTime(minTime);
        result.setOffset(offset + postVOS.size());
        return Result.ok(result);
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
            postVO.setLikeCount(dataCacheUtil.getLikeCount(record.getId()));
            if (userId == null) {
                postVO.setLiked(false);
            } else {
                postVO.setLiked(dataCacheUtil.isLiked(record.getId(), userId));
            }
            postVOS.add(postVO);
        }
        return Result.ok(postVOS, postIPage.getTotal());
    }

    @Transactional
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
        List<Long> fanIds = getFanIds(userId);
        // 将他的粉丝的缓存中的数据清除
        for (Long fanId : fanIds) {
            redisTemplate.opsForZSet().remove(KeyConstant.POST_LIST_KEY + fanId, id);
        }
        // 首页帖子删除
        redisTemplate.opsForZSet().remove(KeyConstant.POST_LIST_KEY, id);
        // 首页帖子表删除
        homePostMapper.delete(new LambdaQueryWrapper<HomePost>().eq(HomePost::getPostId, id));
        redisTemplate.opsForZSet().remove(KeyConstant.HOME_POST_LIST_KEY, id);
        // 收件箱表删除
        userInboxMapper.delete(new LambdaQueryWrapper<UserInbox>().eq(UserInbox::getPostId, id));
        // 我的帖子删除
        redisTemplate.opsForZSet().remove(KeyConstant.POST_KEY + userId, id);
        // 删除帖子的评论
        commentMapper.delete(new LambdaQueryWrapper<Comment>().eq(Comment::getPostId, id));
        // 删除帖子的图片
        List<File> files = fileMapper.selectList(new LambdaQueryWrapper<File>().eq(File::getPostId, id));
        fileMapper.delete(new LambdaQueryWrapper<File>().eq(File::getPostId, id));
        for (File file : files) {
            deleteFileUtil.deleteFile(file.getUrl());
        }
        String cover = post.getCover();
        if (cover != null && !cover.isEmpty()) {
            // 删除封面图片
            deleteFileUtil.deleteFile(cover.substring(systemConstants.baseUrl.length()));
        }
        // 删除帖子点赞数据
        likeMapper.delete(new LambdaQueryWrapper<LikeRecord>().eq(LikeRecord::getPostId, id));
        // 删除帖子redis的点赞数据
        try {
            redisTemplate.opsForSet().remove(KeyConstant.LIKE_KEY + id);
        } catch (Exception e) {
            log.error("删除帖子点赞数据失败", e);
        }
        redisTemplate.delete(KeyConstant.LIKE_COUNT + id);
        // 删除Elasticsearch的数据
        postElasticsearchRepository.deleteById(id);

        return Result.ok(MessageConstant.DELETE_SUCCESS, "");
    }

    @Override
    public Result generatePostId() {
        Long id = redisIdUtils.nextId(KeyConstant.POST_ID_KEY);
        return Result.ok(id);
    }

    private List<Long> getFanIds(Long userId) {
        Set<Object> fans = redisTemplate.opsForZSet().range(KeyConstant.FANS_LIST_KEY + userId, 0, -1);
        if (fans == null || fans.isEmpty()) {
            return List.of();
        }
        // 解析
        return fans.stream().map(fan -> Long.valueOf(fan.toString())).toList();
    }

    /**
     * 记录帖子浏览量，同一用户对同一帖子20秒内只计一次
     */
    @Override
    public Result recordView(Long postId) {
        if (postId == null) {
            return Result.error(MessageConstant.ID_IS_NULL);
        }
        Long userId = userIdUtil.getUserId();
        if (userId == null) {
            return Result.error(MessageConstant.USER_NOT_LOGIN);
        }
        String cooldownKey = KeyConstant.POST_VIEW_COOLDOWN + userId + ":" + postId;
        Boolean exists = redisTemplate.hasKey(cooldownKey);
        if (exists) {
            return Result.ok("已浏览");
        }
        // 增加浏览量
        String viewCountKey = KeyConstant.POST_VIEW_COUNT + postId;
        redisTemplate.opsForValue().increment(viewCountKey);
        // 设置20秒冷却
        redisTemplate.opsForValue().set(cooldownKey, 1, 20, TimeUnit.SECONDS);
        return Result.ok("浏览成功");
    }

    @Override
    public Result migratePostListToHomePost() {
        String postListKey = KeyConstant.POST_LIST_KEY;
        // 读取 Redis 中所有帖子ID（按分数正序，即时间正序）
        Set<ZSetOperations.TypedTuple<Object>> typedTuples = redisTemplate.opsForZSet()
                .rangeWithScores(postListKey, 0, -1);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok("Redis 中无帖子数据", 0);
        }
        int count = 0;
        for (ZSetOperations.TypedTuple<Object> tuple : typedTuples) {
            Long postId = Long.parseLong(tuple.getValue().toString());
            Double score = tuple.getScore();
            // 查询帖子是否存在
            Post post = postMapper.selectById(postId);
            if (post == null) {
                continue;
            }
            // 检查是否已在首页帖子表中
            HomePost existing = homePostMapper.selectOne(
                    new LambdaQueryWrapper<HomePost>().eq(HomePost::getPostId, postId));
            if (existing != null) {
                continue;
            }
            HomePost homePost = new HomePost();
            homePost.setPostId(postId);
            homePost.setUserId(post.getUserId());
            homePostMapper.insert(homePost);
            count++;
        }
        // 同步到首页帖子缓存
        loadHomePostFromDB();
        log.info("迁移完成，共迁移 {} 条帖子到首页帖子表", count);
        return Result.ok("迁移完成", count);
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
            postVO.setLikeCount(dataCacheUtil.getLikeCount(id));
            if (userId == null) {
                postVO.setLiked(false);
            } else {
                postVO.setLiked(dataCacheUtil.isLiked(id, userId));
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
