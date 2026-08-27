package com.li.socialplatform.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.common.utils.HtmlUtils;
import com.li.socialplatform.pojo.entity.Category;
import com.li.socialplatform.pojo.entity.HomePost;
import com.li.socialplatform.pojo.entity.Post;
import com.li.socialplatform.pojo.entity.User;
import com.li.socialplatform.server.mapper.CategoryMapper;
import com.li.socialplatform.server.mapper.HomePostMapper;
import com.li.socialplatform.server.mapper.PostMapper;
import com.li.socialplatform.server.mapper.UserMapper;
import com.li.socialplatform.server.repository.PostElasticsearchRepository;
import com.li.socialplatform.server.repository.UserElasticsearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 启动数据自愈与初始化检查器：
 * 1. 自动同步种子数据到 home_post 表
 * 2. 自动预热 Redis 首页帖子 ZSet 缓存和分类列表
 * 3. 自动创建 Elasticsearch 索引、Mapping 并同步 MySQL 帖子和用户数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final PostMapper postMapper;
    private final HomePostMapper homePostMapper;
    private final UserMapper userMapper;
    private final CategoryMapper categoryMapper;
    private final PostElasticsearchRepository postElasticsearchRepository;
    private final UserElasticsearchRepository userElasticsearchRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        log.info("开始执行数据自愈与初始化同步检查...");
        try {
            initUserPasswords();
            initHomePostsAndRedis();
            initElasticsearch();
            initCategoryCache();
            log.info("数据自愈与初始化同步检查完成！");
        } catch (Exception e) {
            log.warn("数据初始化同步过程中发生异常（不阻塞应用正常运行）: {}", e.getMessage(), e);
        }
    }

    private void initUserPasswords() {
        try {
            List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>().likeRight(User::getPassword, "{bcrypt}"));
            if (users != null && !users.isEmpty()) {
                log.info("检测到 {} 个用户密码含有旧版 {bcrypt} 前缀，自动修复中...", users.size());
                for (User user : users) {
                    user.setPassword(user.getPassword().substring(8));
                    userMapper.updateById(user);
                }
                log.info("用户密码前缀修复完成！");
            }
        } catch (Exception e) {
            log.warn("检查/修复用户密码前缀时异常: {}", e.getMessage());
        }
    }

    private void initHomePostsAndRedis() {
        Long homeCount = homePostMapper.selectCount(null);
        List<Post> allPosts = postMapper.selectList(
                new LambdaQueryWrapper<Post>().eq(Post::getEnabled, true).orderByDesc(Post::getCreateTime));

        if (allPosts.isEmpty()) {
            return;
        }

        String homePostKey = KeyConstant.HOME_POST_LIST_KEY;

        // 若 home_post 表为空，自动从 post 表同步数据
        if (homeCount == 0) {
            log.info("检测到 home_post 表为空，自动从 post 表同步 {} 条首页帖子...", allPosts.size());
            for (Post p : allPosts) {
                HomePost hp = new HomePost();
                hp.setPostId(p.getId());
                hp.setUserId(p.getUserId());
                hp.setCreateTime(p.getCreateTime());
                homePostMapper.insert(hp);
            }
        }

        // 预热 Redis 首页 ZSet 缓存
        Long zsetSize = redisTemplate.opsForZSet().size(homePostKey);
        if (zsetSize == null || zsetSize == 0) {
            for (Post p : allPosts) {
                double score = p.getCreateTime() != null
                        ? p.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        : (double) System.currentTimeMillis();
                redisTemplate.opsForZSet().add(homePostKey, p.getId(), score);
                redisTemplate.opsForZSet().add(KeyConstant.POST_LIST_KEY, p.getId(), score);
            }
            redisTemplate.expire(homePostKey, 7, TimeUnit.DAYS);
            log.info("已将 {} 条帖子预热至 Redis 首页缓存 ({})", allPosts.size(), homePostKey);
        }
    }

    private void initElasticsearch() {
        try {
            // 1. 初始化 Post 索引与 Mapping（配置 IK 中文分词器）
            IndexOperations postIndexOps = elasticsearchOperations.indexOps(Post.class);
            if (!postIndexOps.exists()) {
                log.info("检测到 Elasticsearch post 索引不存在，正在自动创建索引并配置 IK 分词 Mapping...");
                postIndexOps.createWithMapping();
            }

            // 2. 初始化 User 索引与 Mapping
            IndexOperations userIndexOps = elasticsearchOperations.indexOps(User.class);
            if (!userIndexOps.exists()) {
                log.info("检测到 Elasticsearch user 索引不存在，正在自动创建索引并配置 Mapping...");
                userIndexOps.createWithMapping();
            }

            // 3. 自动同步帖子到 ES
            long esPostCount = postElasticsearchRepository.count();
            List<Post> posts = postMapper.selectList(new LambdaQueryWrapper<Post>().eq(Post::getEnabled, true));
            if (esPostCount == 0 && !posts.isEmpty()) {
                log.info("检测到 Elasticsearch 帖子数据为空，自动同步 {} 条帖子到 ES...", posts.size());
                List<Post> esPosts = posts.stream().map(p -> {
                    Post ep = new Post();
                    ep.setId(p.getId());
                    ep.setUserId(p.getUserId());
                    ep.setCover(p.getCover());
                    ep.setTitle(p.getTitle());
                    ep.setContent(HtmlUtils.htmlToPlainText(p.getContent()));
                    ep.setCategoryId(p.getCategoryId());
                    ep.setCreateTime(p.getCreateTime());
                    ep.setEnabled(p.getEnabled());
                    ep.setLikeCount(p.getLikeCount() != null ? p.getLikeCount() : 0);
                    ep.setViewCount(p.getViewCount() != null ? p.getViewCount() : 0);
                    return ep;
                }).toList();
                postElasticsearchRepository.saveAll(esPosts);
                log.info("Elasticsearch 帖子索引同步完成，共同步 {} 条数据", esPosts.size());
            }

            // 4. 自动同步用户到 ES
            long esUserCount = userElasticsearchRepository.count();
            List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>().eq(User::getEnabled, true));
            if (esUserCount == 0 && !users.isEmpty()) {
                log.info("检测到 Elasticsearch 用户数据为空，自动同步 {} 位用户到 ES...", users.size());
                userElasticsearchRepository.saveAll(users);
                log.info("Elasticsearch 用户索引同步完成，共同步 {} 位用户", users.size());
            }
        } catch (Exception e) {
            log.warn("Elasticsearch 数据初始化同步跳过或失败（ES 可能尚未就绪）: {}", e.getMessage());
        }
    }

    private void initCategoryCache() {
        String key = KeyConstant.CATEGORY_LIST_KEY;
        Long size = redisTemplate.opsForList().size(key);
        if (size == null || size == 0) {
            List<Category> categories = categoryMapper.selectList(new LambdaQueryWrapper<>());
            for (Category category : categories) {
                redisTemplate.opsForList().rightPush(key, category);
            }
            redisTemplate.expire(key, 10, TimeUnit.MINUTES);
            log.info("已预热 {} 个分类到 Redis 缓存", categories.size());
        }
    }
}
