package com.li.socialplatform.server.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.socialplatform.common.constant.AuthorityConstant;
import com.li.socialplatform.common.constant.CacheTtl;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.common.constant.MessageConstant;
import com.li.socialplatform.common.properties.SystemConstants;
import com.li.socialplatform.common.utils.*;
import com.li.socialplatform.pojo.dto.PostDTO;
import com.li.socialplatform.pojo.entity.*;
import com.li.socialplatform.pojo.entity.Result;
import com.li.socialplatform.pojo.vo.PostDetailVO;
import com.li.socialplatform.pojo.vo.PostVO;
import com.li.socialplatform.pojo.vo.TagVO;
import com.li.socialplatform.recommend.HomeFeedService;
import com.li.socialplatform.server.mapper.*;
import com.li.socialplatform.server.repository.PostElasticsearchRepository;
import com.li.socialplatform.server.service.ICategoryService;
import com.li.socialplatform.server.service.IPostService;
import com.li.socialplatform.server.service.ITagService;
import com.li.socialplatform.server.service.helper.PostHydrationHelper;
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
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
    private final RedisUtil redisUtil;
    private final ViewCountCacheUtil viewCountCacheUtil;
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
    private final StarRecordMapper starRecordMapper;
    private final CommentLikeRecordMapper commentLikeRecordMapper;
    private final BlacklistUtil blacklistUtil;
    private final HomeFeedService homeFeedService;
    private final ITagService tagService;
    private final ICategoryService categoryService;
    private final PostHydrationHelper postHydrationHelper;
    private final PostTagMapper postTagMapper;

    /** 默认分类ID：social_platform.sql 中 category 表种子数据 id=1 为“其他” */
    private static final Integer DEFAULT_CATEGORY_ID = 1;

    // 获取当前登录用户的用户名
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }

    private final UserMapper userMapper;

    @Transactional
    @Override
    public Result publishPost(PostDTO postDTO) {
        // 获取当前登录用户id
        Long id = userIdUtil.getUserId();
        User user = userMapper.selectById(id);
        if (user == null) {
            return Result.error(MessageConstant.USER_NOT_FOUND);
        }
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
        Integer targetCategoryId = postDTO.getCategoryId() == null ? DEFAULT_CATEGORY_ID : postDTO.getCategoryId();
        Category category = categoryMapper.selectById(targetCategoryId);
        if (category == null || !Boolean.TRUE.equals(category.getEnabled())) {
            return Result.error(MessageConstant.CATEGORY_NOT_EXIST);
        }
        List<String> normalizedTags;
        try {
            normalizedTags = tagService.normalizeTagNames(postDTO.getTags());
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
        post.setCategoryId(targetCategoryId);
        boolean isPrivate = Boolean.TRUE.equals(postDTO.getIsPrivate());
        post.setIsPrivate(isPrivate);
        post.setCommentCount(0);
        postMapper.insert(post);
        List<TagVO> boundTags = tagService.bindPostTags(post.getId(), targetCategoryId, normalizedTags, isPrivate);
        log.info("用户 {} 发表了帖子 {}（私密: {}）", id, post.getId(), isPrivate);
        long time = System.currentTimeMillis();
        // 将帖子添加到缓存中
        redisUtil.zsets().add(KeyConstant.POST_LIST_KEY, post.getId(), time);
        redisUtil.zsets().add(KeyConstant.POST_KEY + id, post.getId(), time);
        // 私密帖子不进入首页推荐流，也不推送给粉丝
        if (!isPrivate) {
            // 将帖子添加到首页帖子表
            HomePost homePost = new HomePost();
            homePost.setPostId(post.getId());
            homePost.setUserId(id);
            homePostMapper.insert(homePost);
            // 添加到首页帖子缓存
            String homePostKey = KeyConstant.HOME_POST_LIST_KEY;
            redisUtil.zsets().add(homePostKey, post.getId(), time);
            redisUtil.expire(homePostKey, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
            // 查询所有粉丝
            List<Long> fanIds = getFanIds(id);
            // 将帖子 pipeline 批量添加到粉丝缓存
            if (!fanIds.isEmpty()) {
                redisUtil.pipeline(ops -> {
                    for (Long fanId : fanIds) {
                        ops.opsForZSet().add(KeyConstant.POST_LIST_KEY + fanId, post.getId(), time);
                    }
                });
                // 异步写入粉丝收件箱表
                asyncTaskUtil.asyncInsertUserInbox(fanIds, post.getId(), id);
            }
        }
        post.setLikeCount(0);
        post.setStarCount(0);
        post.setCommentCount(0);
        post.setEnabled(true);
        post.setTagIds(boundTags.stream().map(TagVO::getId).toList());
        post.setTagNames(boundTags.stream().map(TagVO::getName).toList());
        // 转换为纯文本
        String text = HtmlUtils.htmlToPlainText(postDTO.getContent());
        post.setContent(text);
        // 添加到ES中
        postElasticsearchRepository.save(post);
        if (!isPrivate) {
            categoryService.evictCategoryCache();
            userIntersetScoreUtil.recordPostInteraction(id, targetCategoryId, post.getTagIds(), 3, 4);
        }
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
        if (loginUser != null) {
            if (!post.getEnabled() && !AuthorityConstant.REVIEWER.equals(loginUser.getAuthorityId()) && !AuthorityConstant.ADMIN.equals(loginUser.getAuthorityId())) {
                return Result.error(MessageConstant.POST_NOT_EXIST);
            }
        } else {
            if (!post.getEnabled()) {
                return Result.error(MessageConstant.POST_NOT_EXIST);
            }
        }
        // 黑名单：双方互相不可见（拉黑者与被拉黑者都看不到对方的内容）
        if (blacklistUtil.isHidden(userId, post.getUserId())) {
            return Result.error(MessageConstant.POST_NOT_EXIST);
        }
        // 私密帖子仅作者本人可见（管理员/审核员可放行用于内容管理）
        boolean isModerator = loginUser != null
                && (AuthorityConstant.REVIEWER.equals(loginUser.getAuthorityId()) || AuthorityConstant.ADMIN.equals(loginUser.getAuthorityId()));
        if (Boolean.TRUE.equals(post.getIsPrivate())
                && !Objects.equals(userId, post.getUserId()) && !isModerator) {
            return Result.error(MessageConstant.POST_NOT_EXIST);
        }
        // 只有确认「这篇帖子对当前访问者可见」之后才计入兴趣分，
        // 并按 5 分钟防刷冷却记录浏览兴趣（分类 +1、标签 +2）
        List<TagVO> postTags = tagService.getTagsByPostIds(List.of(id)).getOrDefault(id, List.of());
        if (userId != null && !Boolean.TRUE.equals(post.getIsPrivate())) {
            userIntersetScoreUtil.recordViewWithCooldown(
                    userId,
                    post.getId(),
                    post.getCategoryId(),
                    postTags.stream().map(TagVO::getId).toList());
        }
        User user = userMapper.selectById(post.getUserId());
        if (user == null) {
            return Result.error(MessageConstant.USER_NOT_FOUND);
        }
        // 查询是否点过赞
        PostDetailVO postDetailVO = BeanUtil.copyProperties(post, PostDetailVO.class);
        Category category = categoryMapper.selectById(post.getCategoryId());
        postDetailVO.setCategoryId(post.getCategoryId());
        postDetailVO.setCategory(category != null ? category.getName() : "未分类");
        postDetailVO.setTags(postTags);
        postDetailVO.setCommentCount(post.getCommentCount() == null ? 0 : post.getCommentCount());
        postDetailVO.setLiked(dataCacheUtil.isLiked(id, userId));
        postDetailVO.setLikeCount(dataCacheUtil.getLikeCount(id));
        postDetailVO.setAvatar(user.getAvatar());
        postDetailVO.setNickname(user.getNickname());
        postDetailVO.setCover(post.getCover());
        postDetailVO.setFollowed(dataCacheUtil.isFollowed(userId, user.getId()));
        // 收藏信息
        postDetailVO.setStarCount(post.getStarCount() == null ? 0 : post.getStarCount());
        postDetailVO.setStarred(userId != null && isStarred(userId, id));
        // 读取浏览量（Redis 中待同步的增量 + DB 中已持久化的值）
        long viewDelta = viewCountCacheUtil.getViewDelta(id);
        postDetailVO.setViewCount((post.getViewCount() == null ? 0 : post.getViewCount()) + (int) viewDelta);
        return Result.ok(postDetailVO);
    }

    @Override
    public Result listPosts(Long lastId, Integer offset) {
        return listPosts(lastId, offset, "recommend", false, null, null);
    }

    @Override
    public Result listPosts(Long lastId, Integer offset, String mode, Boolean refresh, Long seed, String visitorId) {
        return homeFeedService.loadHomeFeed(lastId, offset, mode, refresh, seed, visitorId);
    }

    @Override
    public Result listPostsByTag(Long tagId, String tagName, Integer pageNum, Integer pageSize) {
        Tag tag = tagService.resolveTag(tagId, tagName);
        if (tag == null) {
            return Result.ok(List.of(), 0L);
        }
        List<Long> postIds = postTagMapper.selectList(
                        new LambdaQueryWrapper<PostTag>()
                                .select(PostTag::getPostId)
                                .eq(PostTag::getTagId, tag.getId()))
                .stream()
                .map(PostTag::getPostId)
                .distinct()
                .toList();
        if (postIds.isEmpty()) {
            return Result.ok(List.of(), 0L);
        }
        Long viewerId = userIdUtil.getUserId();
        Set<Long> hidden = blacklistUtil.hiddenUserIds(viewerId);
        List<Long> disabledAuthorIds = userMapper.selectList(
                        new LambdaQueryWrapper<User>().select(User::getId).eq(User::getEnabled, false))
                .stream().map(User::getId).toList();
        Set<Long> excludedUsers = new HashSet<>(hidden);
        excludedUsers.addAll(disabledAuthorIds);

        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>()
                .in(Post::getId, postIds)
                .eq(Post::getEnabled, true)
                .and(w -> w.eq(Post::getIsPrivate, false).or().isNull(Post::getIsPrivate))
                .orderByDesc(Post::getLikeCount)
                .orderByDesc(Post::getCreateTime);
        if (!excludedUsers.isEmpty()) {
            wrapper.notIn(Post::getUserId, excludedUsers);
        }
        IPage<Post> page = postMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<PostVO> vos = postHydrationHelper.hydrate(page.getRecords(), viewerId, true, null);
        return Result.ok(vos, page.getTotal());
    }

    /**
     * 从 home_post 表加载数据到 Redis 缓存
     */
    private void loadHomePostFromDB() {
        String homePostKey = KeyConstant.HOME_POST_LIST_KEY;
        String emptyKey = KeyConstant.HOME_POST_EMPTY_KEY;
        List<HomePost> homePosts = homePostMapper.selectList(
                new LambdaQueryWrapper<HomePost>().orderByDesc(HomePost::getCreateTime));
        if (homePosts.isEmpty()) {
            // 空结果防穿透标记
            redisUtil.set(emptyKey, 1, CacheTtl.EMPTY_MARKER_MINUTES, TimeUnit.MINUTES);
            return;
        }
        Map<Object, Double> memberScores = new HashMap<>(homePosts.size());
        for (HomePost hp : homePosts) {
            memberScores.put(hp.getPostId(), toEpochMilli(hp.getCreateTime()));
        }
        redisUtil.zAddAll(homePostKey, memberScores);
        redisUtil.expire(homePostKey, CacheTtl.DEFAULT_DAYS, TimeUnit.DAYS);
        log.info("从 home_post 表加载 {} 条首页帖子到缓存", homePosts.size());
    }

    /** LocalDateTime 转毫秒时间戳，空值取当前时间 */
    private double toEpochMilli(java.time.LocalDateTime createTime) {
        return createTime != null
                ? createTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                : (double) System.currentTimeMillis();
    }

    @Override
    public Result listFollowPosts(Long lastId, Integer offset) {
        // 获取当前用户
        Long userId = userIdUtil.getUserId();
        if (userId == null) {
            return Result.error(MessageConstant.USER_NOT_LOGIN);
        }
        String key = KeyConstant.POST_LIST_KEY + userId;
        Set<ZSetOperations.TypedTuple<Object>> typedTuples = redisUtil.zsets()
                .reverseRangeByScoreWithScores(key,
                        0, lastId, offset, Long.parseLong(systemConstants.defaultPageSize));
        if ((typedTuples == null || typedTuples.isEmpty()) && (offset == null || offset == 0)) {
            loadFollowPostsToCache(userId);
            typedTuples = redisUtil.zsets()
                    .reverseRangeByScoreWithScores(key,
                            0, lastId, offset != null ? offset : 0, Long.parseLong(systemConstants.defaultPageSize));
        }
        return Result.ok(getScrollResult(typedTuples, userId));
    }

    private void loadFollowPostsToCache(Long userId) {
        String key = KeyConstant.POST_LIST_KEY + userId;
        List<UserInbox> inboxes = userInboxMapper.selectList(new LambdaQueryWrapper<UserInbox>()
                .eq(UserInbox::getUserId, userId)
                .orderByDesc(UserInbox::getCreateTime)
                .last("LIMIT 50"));
        if (!inboxes.isEmpty()) {
            Map<Object, Double> memberScores = new HashMap<>(inboxes.size());
            for (UserInbox inbox : inboxes) {
                memberScores.put(inbox.getPostId(), toEpochMilli(inbox.getCreateTime()));
            }
            redisUtil.zAddAll(key, memberScores);
            return;
        }
        // 如果 inbox 为空，从 follow 关系查询关注的用户最近发布的帖子
        Set<Object> followeeIds = redisUtil.zsets().range(KeyConstant.Follow_LIST_KEY + userId, 0, -1);
        List<Long> targetIds = new ArrayList<>();
        if (followeeIds != null && !followeeIds.isEmpty()) {
            for (Object fId : followeeIds) {
                try {
                    targetIds.add(Long.valueOf(fId.toString()));
                } catch (Exception ignored) {}
            }
        }
        if (!targetIds.isEmpty()) {
            List<Post> recentPosts = postMapper.selectList(new LambdaQueryWrapper<Post>()
                    .in(Post::getUserId, targetIds)
                    .eq(Post::getEnabled, true)
                    .orderByDesc(Post::getCreateTime)
                    .last("LIMIT 50"));
            Map<Object, Double> memberScores = new HashMap<>(recentPosts.size());
            for (Post p : recentPosts) {
                memberScores.put(p.getId(), toEpochMilli(p.getCreateTime()));
            }
            redisUtil.zAddAll(key, memberScores);
        }
    }

    @Override
    public Result userListPosts(Long id, Integer pageNum, Integer pageSize) {
        IPage<Post> page = new Page<>(pageNum, pageSize);
        Long userId = userIdUtil.getUserId();
        // 与 getScrollResult 行为一致：主页列表不展示已封禁帖子；
        // 私密帖子仅作者本人在自己主页可见
        boolean isSelf = userId != null && userId.equals(id);
        // 与本人互相拉黑：主页整体不可见（帖子列表返回空）
        java.util.Set<Long> hidden = blacklistUtil.hiddenUserIds(userId);
        if (userId != null && hidden.contains(id)) {
            return Result.ok(new ArrayList<>(), 0L);
        }
        IPage<Post> postIPage = postMapper.selectPage(page, new LambdaQueryWrapper<Post>()
                .eq(Post::getUserId, id)
                .eq(Post::getEnabled, true)
                .eq(!isSelf, Post::getIsPrivate, false)
                .notIn(!hidden.isEmpty(), Post::getUserId, hidden));
        List<Post> records = postIPage.getRecords();
        List<PostVO> postVOS = postHydrationHelper.hydrate(records, userId, !isSelf, null);
        return Result.ok(postVOS, postIPage.getTotal());
    }

    /**
     * 设置帖子私密状态（仅作者本人可操作）。
     * 设为私密时从首页推荐流移除；恢复公开时重新加入。
     */
    @Transactional
    @Override
    public Result updatePostPrivacy(Long id, Boolean isPrivate) {
        Long userId = userIdUtil.getUserId();
        Post post = postMapper.selectOne(new LambdaQueryWrapper<Post>().eq(Post::getUserId, userId).eq(Post::getId, id));
        if (post == null) {
            return Result.error(MessageConstant.POST_NOT_EXIST);
        }
        boolean targetPrivate = Boolean.TRUE.equals(isPrivate);
        boolean wasPrivate = Boolean.TRUE.equals(post.getIsPrivate());
        if (!Objects.equals(wasPrivate, targetPrivate)) {
            Post update = new Post();
            update.setId(id);
            update.setIsPrivate(targetPrivate);
            postMapper.updateById(update);
            tagService.onPostPrivacyChanged(id, wasPrivate, targetPrivate);
            categoryService.evictCategoryCache();
            // 同步 ES（保留 ES 中的纯文本正文与标签，不做整实体覆盖）
            Post esPost = postElasticsearchRepository.findById(id).orElse(null);
            if (esPost != null) {
                esPost.setIsPrivate(targetPrivate);
                List<TagVO> tags = tagService.getTagsByPostIds(List.of(id)).getOrDefault(id, List.of());
                esPost.setTagIds(tags.stream().map(TagVO::getId).toList());
                esPost.setTagNames(tags.stream().map(TagVO::getName).toList());
                postElasticsearchRepository.save(esPost);
            }
            long time = post.getCreateTime() != null
                    ? post.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    : System.currentTimeMillis();
            if (targetPrivate) {
                homePostMapper.delete(new LambdaQueryWrapper<HomePost>().eq(HomePost::getPostId, id));
                redisUtil.zsets().remove(KeyConstant.HOME_POST_LIST_KEY, id);
                redisUtil.zsets().remove(KeyConstant.POST_LIST_KEY, id);
            } else {
                HomePost homePost = new HomePost();
                homePost.setPostId(id);
                homePost.setUserId(post.getUserId());
                homePostMapper.insert(homePost);
                redisUtil.zsets().add(KeyConstant.HOME_POST_LIST_KEY, id, time);
                redisUtil.zsets().add(KeyConstant.POST_LIST_KEY, id, time);
            }
        }
        return Result.ok("操作成功", "");
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
        // 将他的粉丝的缓存中的数据批量清除（pipeline 一次往返）
        if (!fanIds.isEmpty()) {
            redisUtil.pipeline(ops -> {
                for (Long fanId : fanIds) {
                    ops.opsForZSet().remove(KeyConstant.POST_LIST_KEY + fanId, id);
                }
            });
        }
        // 首页帖子删除
        redisUtil.zsets().remove(KeyConstant.POST_LIST_KEY, id);
        // 首页帖子表删除
        homePostMapper.delete(new LambdaQueryWrapper<HomePost>().eq(HomePost::getPostId, id));
        redisUtil.zsets().remove(KeyConstant.HOME_POST_LIST_KEY, id);
        // 收件箱表删除
        userInboxMapper.delete(new LambdaQueryWrapper<UserInbox>().eq(UserInbox::getPostId, id));
        // 我的帖子删除
        redisUtil.zsets().remove(KeyConstant.POST_KEY + userId, id);
        // 删除帖子的评论
        List<Comment> comments = commentMapper.selectList(new LambdaQueryWrapper<Comment>().eq(Comment::getPostId, id));
        commentMapper.delete(new LambdaQueryWrapper<Comment>().eq(Comment::getPostId, id));
        // 删除评论的点赞记录
        if (!comments.isEmpty()) {
            List<Long> commentIds = comments.stream().map(Comment::getId).toList();
            commentLikeRecordMapper.delete(new LambdaQueryWrapper<CommentLikeRecord>()
                    .in(CommentLikeRecord::getCommentId, commentIds));
        }
        // 删除帖子的收藏记录
        starRecordMapper.delete(new LambdaQueryWrapper<StarRecord>().eq(StarRecord::getPostId, id));
        // 删除帖子的图片
        List<File> files = fileMapper.selectList(new LambdaQueryWrapper<File>().eq(File::getPostId, id));
        fileMapper.delete(new LambdaQueryWrapper<File>().eq(File::getPostId, id));
        for (File file : files) {
            deleteFileUtil.deleteFile(file.getUrl());
        }
        String cover = post.getCover();
        if (cover != null && !cover.isEmpty()) {
            // 删除封面图片：baseUrl 前缀可能因部署环境变化而不再匹配，
            // 直接 substring 会切出非法对象名并删错文件，这里先判断前缀再裁剪
            String baseUrl = systemConstants.baseUrl;
            String coverPath = baseUrl != null && !baseUrl.isEmpty() && cover.startsWith(baseUrl)
                    ? cover.substring(baseUrl.length())
                    : cover;
            deleteFileUtil.deleteFile(coverPath);
        }
        // 删除帖子点赞数据
        likeMapper.delete(new LambdaQueryWrapper<LikeRecord>().eq(LikeRecord::getPostId, id));
        // 删除帖子redis的点赞数据
        try {
            redisUtil.delete(List.of(KeyConstant.LIKE_KEY + id, KeyConstant.LIKE_COUNT + id));
        } catch (Exception e) {
            log.error("删除帖子点赞数据失败", e);
        }
        // 删除帖子标签关联及计数
        tagService.onPostDeleted(id, Boolean.TRUE.equals(post.getIsPrivate()));
        categoryService.evictCategoryCache();
        // 删除Elasticsearch的数据
        postElasticsearchRepository.deleteById(id);

        return Result.ok(MessageConstant.DELETE_SUCCESS, "");
    }

    @Override
    public Result generatePostId() {
        Long id = redisIdUtils.nextId(KeyConstant.POST_ID_KEY);
        return Result.ok(id);
    }

    /** 查询当前用户是否收藏了某帖子 */
    private boolean isStarred(Long userId, Long postId) {
        if (userId == null || postId == null) {
            return false;
        }
        return starRecordMapper.selectCount(new LambdaQueryWrapper<StarRecord>()
                .eq(StarRecord::getUserId, userId)
                .eq(StarRecord::getPostId, postId)) > 0;
    }

    /** 批量查询用户对帖子的收藏状态 */
    private Map<Long, Boolean> getStarredMap(Long userId, Collection<Long> postIds) {
        Map<Long, Boolean> result = new HashMap<>();
        if (userId == null || postIds == null || postIds.isEmpty()) {
            return result;
        }
        List<Long> ids = postIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return result;
        }
        starRecordMapper.selectList(new LambdaQueryWrapper<StarRecord>()
                        .eq(StarRecord::getUserId, userId)
                        .in(StarRecord::getPostId, ids))
                .forEach(r -> result.put(r.getPostId(), true));
        return result;
    }

    private List<Long> getFanIds(Long userId) {
        // 先走 DataCacheUtil 的回源：缓存 miss 时从 follow 表加载并回填双列表，否则粉丝将永久漏帖
        dataCacheUtil.ensureFollowDataLoaded(userId);
        Set<Object> fans = redisUtil.zsets().range(KeyConstant.FANS_LIST_KEY + userId, 0, -1);
        if (fans == null || fans.isEmpty()) {
            return List.of();
        }
        // 解析
        return fans.stream().map(fan -> Long.valueOf(fan.toString())).toList();
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("X-Real-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getRemoteAddr();
                }
                if (ip != null && ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip != null ? ip : "unknown";
            }
        } catch (Exception ignored) {}
        return "unknown";
    }

    /**
     * 记录帖子浏览量，同一用户/IP对同一帖子20秒内只计一次
     */
    @Override
    public Result recordView(Long postId) {
        if (postId == null) {
            return Result.error(MessageConstant.ID_IS_NULL);
        }
        Long userId = userIdUtil.getUserId();
        String identifier = userId != null ? "u:" + userId : "ip:" + getClientIp();
        boolean recorded = viewCountCacheUtil.recordView(postId, identifier);
        return recorded ? Result.ok("浏览成功") : Result.ok("已浏览");
    }

    @Override
    public Result migratePostListToHomePost() {
        String postListKey = KeyConstant.POST_LIST_KEY;
        // 读取 Redis 中所有帖子ID（按分数正序，即时间正序）
        Set<ZSetOperations.TypedTuple<Object>> typedTuples = redisUtil.zsets()
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

        // 批量查询帖子
        Map<Long, Post> postMap = new HashMap<>();
        List<Post> posts = postMapper.selectBatchIds(ids);
        posts.forEach(p -> postMap.put(p.getId(), p));

        // 黑名单作者：与本人互相不可见（读取期过滤，缓存里的旧条目也会被挡掉）
        java.util.Set<Long> hiddenAuthors = blacklistUtil.hiddenUserIds(userId);
        List<Post> eligiblePosts = new ArrayList<>();
        for (Long id : ids) {
            Post post = postMap.get(id);
            if (post == null || !Boolean.TRUE.equals(post.getEnabled())) {
                continue;
            }
            if (Boolean.TRUE.equals(post.getIsPrivate())) {
                continue;
            }
            if (post.getUserId() != null && hiddenAuthors.contains(post.getUserId())) {
                continue;
            }
            eligiblePosts.add(post);
        }
        List<PostVO> postVOS = postHydrationHelper.hydrate(eligiblePosts, userId, true, null);
        int newOffset = 1;
        double score = scores.getFirst();
        for (int i = 1; i < scores.size(); i++) {
            if (score == scores.get(i)) {
                newOffset++;
            } else {
                newOffset = 1;
            }
            score = scores.get(i);
        }
        ScrollResult<PostVO> postVOScrollResult = new ScrollResult<>();
        postVOScrollResult.setList(postVOS);
        postVOScrollResult.setOffset(newOffset);
        postVOScrollResult.setMinTime(scores.getLast().longValue());
        return postVOScrollResult;
    }
}
