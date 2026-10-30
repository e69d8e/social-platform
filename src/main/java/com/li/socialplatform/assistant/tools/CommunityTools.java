package com.li.socialplatform.assistant.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.socialplatform.pojo.entity.*;
import com.li.socialplatform.server.mapper.*;
import com.li.socialplatform.server.repository.PostElasticsearchRepository;
import com.li.socialplatform.server.repository.UserElasticsearchRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 社区大模型工具集（只读）
 * 提供站内真实帖子检索、热门帖子、创作者搜索、帖子详情与用户上下文能力
 */
@Slf4j
@Component("communityTools")
@RequiredArgsConstructor
public class CommunityTools {

    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final CategoryMapper categoryMapper;
    private final CommentMapper commentMapper;
    private final SessionMapper sessionMapper;
    private final UserInterestScoreMapper userInterestScoreMapper;
    private final PostElasticsearchRepository postElasticsearchRepository;
    private final UserElasticsearchRepository userElasticsearchRepository;

    @Tool("搜索社区内的真实帖子。当用户想寻找特定话题、问题或领域的帖子时调用此工具。")
    public String searchPosts(
            @P("搜索关键词，例如'摄影技巧'、'大模型'、'日常美食'") String keyword,
            @P("分类板块名称，如'摄影'、'AI'、'生活'、'科技'、'数码'，若不限制传空字符串") String categoryName,
            @P("返回结果数量，默认5，最大10") Integer limit
    ) {
        int safeLimit = (limit == null || limit <= 0) ? 5 : Math.min(limit, 10);
        log.info("AI Tool searchPosts called: keyword={}, category={}, limit={}", keyword, categoryName, safeLimit);

        try {
            Integer targetCategoryId = null;
            if (StringUtils.isNotBlank(categoryName)) {
                Category cat = categoryMapper.selectOne(
                        new LambdaQueryWrapper<Category>().eq(Category::getName, categoryName.trim())
                );
                if (cat != null) {
                    targetCategoryId = cat.getId();
                }
            }

            List<Post> posts = new ArrayList<>();
            // 优先尝试 Elasticsearch 检索
            try {
                Pageable pageable = PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "likeCount"));
                if (StringUtils.isNotBlank(keyword)) {
                    posts = postElasticsearchRepository.searchByKeywordAndEnabled(keyword.trim(), pageable);
                } else if (targetCategoryId != null) {
                    posts = postElasticsearchRepository.findByCategoryIdAndEnabledTrue(targetCategoryId, pageable);
                } else {
                    posts = postElasticsearchRepository.findByEnabledTrue(pageable);
                }

                // 若指定了分类且关键词搜索结果包含其他分类，进行过滤
                if (targetCategoryId != null && !posts.isEmpty()) {
                    final Integer finalCatId = targetCategoryId;
                    posts = posts.stream()
                            .filter(p -> finalCatId.equals(p.getCategoryId()))
                            .collect(Collectors.toList());
                }
            } catch (Exception esEx) {
                log.warn("Elasticsearch 检索异常，降级为 MySQL 模糊查询: {}", esEx.getMessage());
                posts = Collections.emptyList();
            }

            // 降级使用 MySQL
            if (posts.isEmpty()) {
                LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>()
                        .eq(Post::getEnabled, true);
                if (targetCategoryId != null) {
                    wrapper.eq(Post::getCategoryId, targetCategoryId);
                }
                if (StringUtils.isNotBlank(keyword)) {
                    wrapper.and(w -> w.like(Post::getTitle, keyword.trim())
                            .or().like(Post::getContent, keyword.trim()));
                }
                wrapper.orderByDesc(Post::getLikeCount)
                        .last("limit " + safeLimit);
                posts = postMapper.selectList(wrapper);
            }

            if (posts.isEmpty()) {
                return "在社区中暂未检索到与 '" + keyword + "' 相关的帖子。建议引导用户发帖分享或换个关键词。";
            }

            return formatPostsResponse(posts);
        } catch (Exception e) {
            log.error("searchPosts execution failed", e);
            return "检索社区帖子时发生内部错误，请稍后重试。";
        }
    }

    @Tool("获取社区当前最热门的帖子列表。当用户询问'最近有什么热门'、'大家在看什么'时调用此工具。")
    public String getHotPosts(@P("获取数量，默认5，最大10") Integer limit) {
        int safeLimit = (limit == null || limit <= 0) ? 5 : Math.min(limit, 10);
        log.info("AI Tool getHotPosts called: limit={}", safeLimit);

        try {
            LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>()
                    .eq(Post::getEnabled, true)
                    .orderByDesc(Post::getLikeCount)
                    .orderByDesc(Post::getViewCount)
                    .last("limit " + safeLimit);
            List<Post> posts = postMapper.selectList(wrapper);

            if (posts.isEmpty()) {
                return "社区目前暂无热门帖子。";
            }

            return formatPostsResponse(posts);
        } catch (Exception e) {
            log.error("getHotPosts execution failed", e);
            return "获取热门帖子时发生错误。";
        }
    }

    @Tool("获取指定帖子的详细内容与热评。当用户对某个具体帖子深入提问、要求总结或探讨时调用此工具。")
    public String getPostDetail(@P("帖子ID") Long postId) {
        if (postId == null) {
            return "请输入有效的帖子ID。";
        }
        log.info("AI Tool getPostDetail called: postId={}", postId);

        try {
            Post post = postMapper.selectById(postId);
            if (post == null || Boolean.FALSE.equals(post.getEnabled())) {
                return "帖子不存在或已被屏蔽/下架。";
            }

            User author = userMapper.selectById(post.getUserId());
            String authorName = (author != null) ? author.getNickname() : "匿名创作者";

            Category category = (post.getCategoryId() != null) ? categoryMapper.selectById(post.getCategoryId()) : null;
            String catName = (category != null) ? category.getName() : "综合";

            // 读取前3条评论
            List<Comment> comments = commentMapper.selectList(
                    new LambdaQueryWrapper<Comment>()
                            .eq(Comment::getPostId, postId)
                            .isNull(Comment::getParentId)
                            .orderByDesc(Comment::getCreateTime)
                            .last("limit 3")
            );

            StringBuilder sb = new StringBuilder();
            sb.append("【帖子详情】\n");
            sb.append("标题：").append(post.getTitle()).append("\n");
            sb.append("分类：").append(catName).append("\n");
            sb.append("作者：").append(authorName).append(" (用户ID: ").append(post.getUserId()).append(")\n");
            sb.append("互动数据：点赞数 ").append(post.getLikeCount()).append("，浏览量 ").append(post.getViewCount()).append("\n");
            sb.append("正文内容：\n").append(post.getContent()).append("\n");

            if (!comments.isEmpty()) {
                sb.append("\n【热门评论精选】：\n");
                for (int i = 0; i < comments.size(); i++) {
                    Comment c = comments.get(i);
                    User cu = userMapper.selectById(c.getUserId());
                    String commenter = (cu != null) ? cu.getNickname() : "用户";
                    sb.append(i + 1).append(". ").append(commenter).append("：").append(c.getContent()).append("\n");
                }
            }

            sb.append("\n提示：在回复用户时，建议使用 [《").append(post.getTitle()).append("》](/post/").append(post.getId()).append(") 引用此帖。");
            return sb.toString();
        } catch (Exception e) {
            log.error("getPostDetail execution failed", e);
            return "获取帖子详情失败。";
        }
    }

    @Tool("搜索社区内的优秀创作者。当用户想找特定领域博主、同好或作者推荐时调用此工具。")
    public String searchCreators(
            @P("搜索关键词，例如'摄影'、'数码'、'程序员'或用户昵称") String keyword,
            @P("返回数量，默认5，最大10") Integer limit
    ) {
        int safeLimit = (limit == null || limit <= 0) ? 5 : Math.min(limit, 10);
        log.info("AI Tool searchCreators called: keyword={}, limit={}", keyword, safeLimit);

        try {
            List<User> users = new ArrayList<>();
            try {
                Pageable pageable = PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "fansCount"));
                users = userElasticsearchRepository.findByUsernameOrNickname(keyword, keyword, pageable);
            } catch (Exception esEx) {
                log.warn("Elasticsearch 用户检索失败，降级为 MySQL: {}", esEx.getMessage());
            }

            if (users.isEmpty()) {
                LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                        .eq(User::getEnabled, 1)
                        .and(w -> w.like(User::getNickname, keyword)
                                .or().like(User::getUsername, keyword)
                                .or().like(User::getBio, keyword))
                        .orderByDesc(User::getFansCount)
                        .last("limit " + safeLimit);
                users = userMapper.selectList(wrapper);
            }

            if (users.isEmpty()) {
                return "在社区中暂未找到与 '" + keyword + "' 匹配的创作者。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("检索到以下社区创作者：\n");
            for (User u : users) {
                sb.append("- [ID: ").append(u.getId()).append("] ")
                        .append("昵称: ").append(u.getNickname())
                        .append("，粉丝数: ").append(u.getFansCount() != null ? u.getFansCount() : 0)
                        .append("，简介: ").append(StringUtils.defaultIfBlank(u.getBio(), "这个人很懒，什么都没写"))
                        .append("，主页链接标识: [@").append(u.getNickname()).append("](/user/").append(u.getId()).append(")\n");
            }
            sb.append("提示：在回复用户时，请使用 [@创作者昵称](/user/{id}) 格式生成可点击链接。");
            return sb.toString();
        } catch (Exception e) {
            log.error("searchCreators execution failed", e);
            return "搜索创作者失败。";
        }
    }

    @Tool("获取社区所有官方分类板块信息。当用户询问有哪些板块、或者发帖需要推荐合适分类时调用此工具。")
    public String getCommunityCategories() {
        log.info("AI Tool getCommunityCategories called");
        try {
            List<Category> categories = categoryMapper.selectList(null);
            if (categories.isEmpty()) {
                return "社区分类列表为空。";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("社区包含以下分类板块：\n");
            for (Category c : categories) {
                sb.append("- [分类ID: ").append(c.getId()).append("] ").append(c.getName())
                        .append("（站内链接: [").append(c.getName()).append("](/category/").append(c.getId()).append(")）\n");
            }
            sb.append("为用户生成发帖草稿推荐分类时，请严格从以上分类名称中选取。");
            return sb.toString();
        } catch (Exception e) {
            log.error("getCommunityCategories execution failed", e);
            return "获取分类列表失败。";
        }
    }

    @Tool("获取当前正在与你对话的用户信息与偏好。当需要针对性称呼用户、或者根据用户喜好进行推荐时调用。")
    public String getCurrentUserInfo(@ToolMemoryId String memoryId) {
        log.info("AI Tool getCurrentUserInfo called: memoryId={}", memoryId);
        if (StringUtils.isBlank(memoryId)) {
            return "当前为访客会话，未获取到登录信息。";
        }

        try {
            Session session = sessionMapper.selectById(memoryId);
            if (session == null || session.getUserId() == null) {
                return "未找到对应会话或用户未登录。";
            }

            User user = userMapper.selectById(session.getUserId());
            if (user == null) {
                return "用户信息未找到。";
            }

            // 查询用户兴趣偏好最高的分类
            List<UserInterestScore> scores = userInterestScoreMapper.selectList(
                    new LambdaQueryWrapper<UserInterestScore>()
                            .eq(UserInterestScore::getUserId, user.getId())
                            .orderByDesc(UserInterestScore::getScore)
                            .last("limit 3")
            );

            List<String> interestNames = new ArrayList<>();
            for (UserInterestScore s : scores) {
                Category c = categoryMapper.selectById(s.getCategoryId());
                if (c != null) {
                    interestNames.add(c.getName());
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("当前提问用户信息：\n");
            sb.append("- 用户ID: ").append(user.getId()).append("\n");
            sb.append("- 昵称: ").append(user.getNickname()).append("\n");
            sb.append("- 个人简介: ").append(StringUtils.defaultIfBlank(user.getBio(), "无")).append("\n");
            sb.append("- 粉丝数: ").append(user.getFansCount() != null ? user.getFansCount() : 0).append("\n");
            if (!interestNames.isEmpty()) {
                sb.append("- 常浏览与感兴趣的板块: ").append(String.join("、", interestNames)).append("\n");
            }
            sb.append("可以在回复中亲切地用昵称称呼对方，并适度结合其兴趣板块进行个性化建议。");
            return sb.toString();
        } catch (Exception e) {
            log.error("getCurrentUserInfo execution failed", e);
            return "获取当前用户信息失败。";
        }
    }

    private String formatPostsResponse(List<Post> posts) {
        // 批量查询分类与作者
        Set<Integer> catIds = posts.stream().map(Post::getCategoryId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Integer, String> catMap = new HashMap<>();
        if (!catIds.isEmpty()) {
            categoryMapper.selectBatchIds(catIds).forEach(c -> catMap.put(c.getId(), c.getName()));
        }

        Set<Long> userIds = posts.stream().map(Post::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            userMapper.selectBatchIds(userIds).forEach(u -> userMap.put(u.getId(), u.getNickname()));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("检索到以下相关帖子：\n");
        for (int i = 0; i < posts.size(); i++) {
            Post p = posts.get(i);
            String catName = catMap.getOrDefault(p.getCategoryId(), "其他");
            String authorName = userMap.getOrDefault(p.getUserId(), "社区用户");

            // 内容简短截取，保护 token
            String excerpt = p.getContent();
            if (excerpt != null) {
                excerpt = excerpt.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim();
                if (excerpt.length() > 100) {
                    excerpt = excerpt.substring(0, 100) + "...";
                }
            } else {
                excerpt = "暂无详细内容";
            }

            sb.append(i + 1).append(". [ID: ").append(p.getId()).append("] ")
                    .append("《").append(p.getTitle()).append("》")
                    .append(" (分类: ").append(catName)
                    .append("，作者: ").append(authorName)
                    .append("，点赞: ").append(p.getLikeCount() != null ? p.getLikeCount() : 0)
                    .append("，浏览: ").append(p.getViewCount() != null ? p.getViewCount() : 0).append(")\n")
                    .append("   摘要: ").append(excerpt).append("\n")
                    .append("   站内链接格式: [《").append(p.getTitle()).append("》](/post/").append(p.getId()).append(")\n");
        }
        sb.append("\n重要提示：在向用户展示推荐帖子时，必须使用 [《帖子标题》](/post/{id}) 格式输出 Markdown 链接，方便用户点击跳转查看！");
        return sb.toString();
    }
}
