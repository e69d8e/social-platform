package com.li.socialplatform.server.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.common.utils.RedisUtil;
import com.li.socialplatform.pojo.entity.HomePost;
import com.li.socialplatform.pojo.entity.Post;
import com.li.socialplatform.server.mapper.HomePostMapper;
import com.li.socialplatform.server.mapper.PostMapper;
import com.li.socialplatform.server.repository.PostElasticsearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 定时发布任务：每分钟扫描一次到期的定时帖子并发布
 *
 * @author e69d8e
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledPublishTask {

    private final PostMapper postMapper;
    private final HomePostMapper homePostMapper;
    private final PostElasticsearchRepository postElasticsearchRepository;
    private final RedisUtil redisUtil;

    @Scheduled(fixedDelay = 60 * 1000) // 每分钟执行一次
    public void publishScheduledPosts() {
        List<Post> due = postMapper.selectList(
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getStatus, "scheduled")
                        .eq(Post::getEnabled, true)
                        .le(Post::getPublishTime, LocalDateTime.now())
                        .last("LIMIT 50"));
        if (due.isEmpty()) return;

        for (Post post : due) {
            try {
                // 更新状态
                postMapper.update(null, new LambdaUpdateWrapper<Post>()
                        .eq(Post::getId, post.getId())
                        .set(Post::getStatus, "published"));

                long time = System.currentTimeMillis();
                // 入推荐流
                redisUtil.zsets().add(KeyConstant.POST_LIST_KEY, post.getId(), time);
                redisUtil.zsets().add(KeyConstant.POST_KEY + post.getUserId(), post.getId(), time);

                if (!Boolean.TRUE.equals(post.getIsPrivate())) {
                    // 首页帖子表
                    HomePost homePost = new HomePost();
                    homePost.setPostId(post.getId());
                    homePost.setUserId(post.getUserId());
                    homePostMapper.insert(homePost);
                    redisUtil.zsets().add(KeyConstant.HOME_POST_LIST_KEY, post.getId(), time);
                    redisUtil.expire(KeyConstant.HOME_POST_LIST_KEY, 7, TimeUnit.DAYS);
                }

                // 写入ES前同步内存状态，避免索引仍保留 scheduled
                post.setStatus("published");
                post.setPublishTime(null);
                postElasticsearchRepository.save(post);
                log.info("定时发布帖子 {} (用户 {})", post.getId(), post.getUserId());
            } catch (Exception e) {
                log.error("定时发布帖子 {} 失败: {}", post.getId(), e.getMessage());
            }
        }
    }
}
