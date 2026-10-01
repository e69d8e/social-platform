package com.li.socialplatform.server.task;

import com.li.socialplatform.common.constant.KeyConstant;
import com.li.socialplatform.common.utils.ViewCountCacheUtil;
import com.li.socialplatform.pojo.entity.Post;
import com.li.socialplatform.server.mapper.PostMapper;
import com.li.socialplatform.server.repository.PostElasticsearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author e69d8e
 * @since 2026/05/23
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ViewCountSyncTask {

    private final ViewCountCacheUtil viewCountCacheUtil;
    private final PostMapper postMapper;
    private final PostElasticsearchRepository postElasticsearchRepository;

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void syncViewCounts() {
        // SCAN 渐进式扫描，代替阻塞的 KEYS 命令
        Set<String> keys = viewCountCacheUtil.scanPendingKeys();
        if (keys.isEmpty()) {
            return;
        }
        log.info("开始同步帖子浏览量, 共 {} 条", keys.size());
        // pipeline 一次往返读取全部待同步增量
        Map<Long, Long> deltas = viewCountCacheUtil.batchGetDeltas(keys);
        int synced = 0;
        List<String> processedKeys = new ArrayList<>(keys.size());
        List<Post> changedPosts = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : deltas.entrySet()) {
            processedKeys.add(KeyConstant.POST_VIEW_COUNT + entry.getKey());
            try {
                if (entry.getValue() <= 0) {
                    continue;
                }
                Post post = postMapper.selectById(entry.getKey());
                if (post == null) {
                    continue;
                }
                int currentDbCount = post.getViewCount() == null ? 0 : post.getViewCount();
                post.setViewCount(currentDbCount + entry.getValue().intValue());
                postMapper.updateById(post);
                changedPosts.add(post);
                synced++;
            } catch (Exception e) {
                log.error("同步帖子浏览量失败: postId={}, error={}", entry.getKey(), e.getMessage());
                processedKeys.remove(KeyConstant.POST_VIEW_COUNT + entry.getKey());
            }
        }
        // ES 批量同步：失败只记日志，DB 已累加为准确值；
        // key 必须照常删除，否则下一轮会把同一批增量再次累加进 DB
        if (!changedPosts.isEmpty()) {
            try {
                postElasticsearchRepository.saveAll(changedPosts);
            } catch (Exception e) {
                log.error("批量同步帖子浏览量到ES失败, 本轮共 {} 条: error={}", changedPosts.size(), e.getMessage());
            }
        }
        // 清理已同步（及脏数据）的 key
        viewCountCacheUtil.deleteKeys(processedKeys);
        log.info("帖子浏览量同步完成, 成功同步 {} 条", synced);
    }
}
