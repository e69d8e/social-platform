package com.li.socialplatform.common.utils;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.li.socialplatform.pojo.entity.Follow;
import com.li.socialplatform.pojo.entity.LikeRecord;
import com.li.socialplatform.pojo.entity.Post;
import com.li.socialplatform.pojo.entity.User;
import com.li.socialplatform.pojo.entity.UserInbox;
import com.li.socialplatform.pojo.entity.UserInterestScore;
import com.li.socialplatform.server.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * @author e69d8e
 * @since 2026/05/23
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AsyncTaskUtil {

    private final ElasticsearchOperations elasticsearchOperations;
    private final LikeMapper likeMapper;
    private final FollowMapper followMapper;
    private final UserInterestScoreMapper userInterestScoreMapper;
    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final UserInboxMapper userInboxMapper;

    @Async("mvcTaskExecutor")
    public void syncPostLikeCount(Long postId, Integer likeCount) {
        try {
            Post post = elasticsearchOperations.get(postId.toString(), Post.class);
            if (post != null) {
                post.setLikeCount(likeCount);
                elasticsearchOperations.save(post);
                // 只定向更新 like_count：ES 中的 content 是纯文本，整实体回写会把 DB 的 HTML 正文覆盖掉
                postMapper.update(null, new LambdaUpdateWrapper<Post>()
                        .eq(Post::getId, postId)
                        .set(Post::getLikeCount, likeCount));
            }
        } catch (Exception e) {
            log.error("异步同步帖子点赞数到ES失败: postId={}, error={}", postId, e.getMessage());
        }
    }

    @Async("mvcTaskExecutor")
    public void syncUserFansCount(Long userId, Integer count) {
        try {
            User user = elasticsearchOperations.get(userId.toString(), User.class);
            if (user != null) {
                user.setFansCount(count);
                elasticsearchOperations.save(user);
                // 只定向更新 fans_count，避免 ES 快照整实体回写覆盖 DB 其他字段
                userMapper.update(null, new LambdaUpdateWrapper<User>()
                        .eq(User::getId, userId)
                        .set(User::getFansCount, count));
            }
        } catch (Exception e) {
            log.error("异步同步用户粉丝数到ES失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    @Async("mvcTaskExecutor")
    public void asyncInsertLikeRecord(Long postId, Long userId) {
        try {
            likeMapper.insert(new LikeRecord(null, postId, userId, null));
        } catch (Exception e) {
            log.error("异步插入点赞记录失败: postId={}, userId={}, error={}", postId, userId, e.getMessage());
        }
    }

    @Async("mvcTaskExecutor")
    public void asyncDeleteLikeRecord(Long postId, Long userId) {
        try {
            likeMapper.delete(new LambdaQueryWrapper<LikeRecord>()
                    .eq(LikeRecord::getPostId, postId)
                    .eq(LikeRecord::getUserId, userId));
        } catch (Exception e) {
            log.error("异步删除点赞记录失败: postId={}, userId={}, error={}", postId, userId, e.getMessage());
        }
    }

    @Async("mvcTaskExecutor")
    public void asyncDeleteFollowRecord(Long followerId, Long followeeId) {
        try {
            followMapper.delete(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getFollowerId, followerId)
                    .eq(Follow::getFolloweeId, followeeId));
        } catch (Exception e) {
            log.error("异步删除关注记录失败: followerId={}, followeeId={}, error={}", followerId, followeeId, e.getMessage());
        }
    }

    @Async("mvcTaskExecutor")
    public void asyncSaveUserInterestScore(Long userId, Integer categoryId, Integer score) {
        try {
            UserInterestScore existing = userInterestScoreMapper.selectOne(
                    new LambdaQueryWrapper<UserInterestScore>()
                            .eq(UserInterestScore::getUserId, userId)
                            .eq(UserInterestScore::getCategoryId, categoryId));
            if (existing == null) {
                userInterestScoreMapper.insert(new UserInterestScore(userId, categoryId, score));
            } else {
                existing.setScore(score);
                userInterestScoreMapper.updateById(existing);
            }
        } catch (Exception e) {
            log.error("异步保存用户兴趣分失败: userId={}, categoryId={}, error={}", userId, categoryId, e.getMessage());
        }
    }

    /**
     * 异步批量插入收件箱记录
     *
     * @param userIds  接收者用户ID列表
     * @param postId   帖子ID
     * @param authorId 发布者ID
     */
    @Async("mvcTaskExecutor")
    public void asyncInsertUserInbox(List<Long> userIds, Long postId, Long authorId) {
        try {
            for (Long userId : userIds) {
                UserInbox inbox = new UserInbox();
                inbox.setUserId(userId);
                inbox.setPostId(postId);
                inbox.setAuthorId(authorId);
                try {
                    userInboxMapper.insert(inbox);
                } catch (Exception e) {
                    // 唯一键冲突忽略（重复推送）
                    log.debug("收件箱记录已存在: userId={}, postId={}", userId, postId);
                }
            }
            log.info("批量插入收件箱记录完成: postId={}, count={}", postId, userIds.size());
        } catch (Exception e) {
            log.error("异步插入收件箱记录失败: postId={}, error={}", postId, e.getMessage());
        }
    }
}
