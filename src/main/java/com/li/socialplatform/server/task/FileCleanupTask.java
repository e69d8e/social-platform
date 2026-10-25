package com.li.socialplatform.server.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.li.socialplatform.common.properties.MinioProperties;
import com.li.socialplatform.common.properties.SystemConstants;
import com.li.socialplatform.pojo.entity.Post;
import com.li.socialplatform.pojo.entity.User;
import com.li.socialplatform.server.mapper.FileMapper;
import com.li.socialplatform.server.mapper.PostMapper;
import com.li.socialplatform.server.mapper.UserMapper;
import com.li.socialplatform.server.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文件自动清理任务：以业务引用为事实源与 MinIO 桶对账。
 *
 * 合法引用包括：file 表记录、帖子封面（post.cover）、用户头像（user.avatar）——
 * 后两类种子/历史数据直接指向 /imgs 对象而不经过上传链路，没有 file 表记录。
 *
 * 1. 孤儿对象：桶内存在但无任何引用的对象（上传成功但入库失败、历史删除失败等）；
 * 2. 废弃草稿：file 表中 post_id 非空但对应帖子不存在（发布页放弃/发布失败）的记录及其对象；
 * 3. 一致性告警：业务引用指向但桶内缺失的对象仅记录日志（无法自动修复）。
 *
 * 宽限期内（默认 24h，按对象最后修改时间）的对象/记录一律不动，
 * 避免误伤"已上传尚未入库 / 草稿仍在编辑"的正常流转窗口。
 *
 * @author e69d8e
 * @since 2026/10/25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileCleanupTask {

    /** 静态资源白名单：随仓库迁移入桶、无业务引用的常驻对象 */
    private static final Set<String> STATIC_OBJECT_WHITELIST = Set.of("avatar/default.jpg", "logo.png");

    private final FileMapper fileMapper;

    private final PostMapper postMapper;

    private final UserMapper userMapper;

    private final FileStorageService fileStorageService;

    private final MinioProperties minioProperties;

    private final SystemConstants systemConstants;

    /**
     * 启动 1 分钟后先跑一轮（便于开发环境尽早观察效果），之后每 6 小时一轮。
     */
    @Scheduled(initialDelay = 60_000, fixedDelay = 6 * 60 * 60 * 1000)
    public void cleanupOrphanFiles() {
        if (!minioProperties.cleanupEnabled) {
            return;
        }
        try {
            doCleanup();
        } catch (Exception e) {
            log.error("文件自动清理任务执行失败", e);
        }
    }

    private void doCleanup() {
        Date threshold = new Date(System.currentTimeMillis() - minioProperties.cleanupGraceHours * 3600_000L);

        // 桶内全量对象：对象名 -> 最后修改时间（缺失修改时间按"刚刚"处理，宽限期内不清理）
        Map<String, Date> objects = fileStorageService.listAllObjects().stream()
                .collect(Collectors.toMap(FileStorageService.StorageObjectInfo::objectKey,
                        info -> info.lastModified() == null ? new Date() : info.lastModified(),
                        (a, b) -> a));
        if (objects.isEmpty()) {
            log.info("文件自动清理：桶内无对象，跳过");
            return;
        }

        // 汇总全部合法引用（file 表 + 帖子封面 + 用户头像）
        List<com.li.socialplatform.pojo.entity.File> allRows = fileMapper.selectList(null);
        Set<String> referencedKeys = new HashSet<>();
        for (com.li.socialplatform.pojo.entity.File f : allRows) {
            referencedKeys.add(toKey(f.getUrl()));
        }
        for (Post p : postMapper.selectList(new LambdaQueryWrapper<Post>().select(Post::getCover))) {
            referencedKeys.add(toKey(p.getCover()));
        }
        for (User u : userMapper.selectList(new LambdaQueryWrapper<User>().select(User::getAvatar))) {
            referencedKeys.add(toKey(u.getAvatar()));
        }
        referencedKeys.remove("");

        // ---- 1. 清理孤儿对象 ----
        int orphanDeleted = 0;
        for (Map.Entry<String, Date> entry : objects.entrySet()) {
            String key = entry.getKey();
            if (STATIC_OBJECT_WHITELIST.contains(key) || referencedKeys.contains(key)) {
                continue;
            }
            if (entry.getValue().after(threshold)) {
                continue; // 宽限期内：可能正处于上传→入库的间隙
            }
            try {
                fileStorageService.delete(key);
                orphanDeleted++;
                log.info("文件自动清理：删除孤儿对象 {}", key);
            } catch (Exception e) {
                log.error("文件自动清理：删除孤儿对象失败 {}", key, e);
            }
        }

        // ---- 2. 清理废弃草稿记录（post_id 非空但帖子不存在）----
        List<com.li.socialplatform.pojo.entity.File> draftRows = allRows.stream()
                .filter(f -> f.getPostId() != null)
                .toList();
        int draftDeleted = 0;
        if (!draftRows.isEmpty()) {
            Set<Long> existingPostIds = postMapper.selectBatchIds(
                            draftRows.stream().map(com.li.socialplatform.pojo.entity.File::getPostId).distinct().toList())
                    .stream().map(Post::getId).collect(Collectors.toSet());
            for (com.li.socialplatform.pojo.entity.File row : draftRows) {
                if (existingPostIds.contains(row.getPostId())) {
                    continue;
                }
                String key = toKey(row.getUrl());
                Date lastModified = objects.get(key);
                if (lastModified == null || lastModified.after(threshold)) {
                    continue; // 宽限期内：草稿可能仍在编辑（对象缺失的留待下一轮告警排查）
                }
                fileMapper.deleteById(row.getId());
                draftDeleted++;
                log.info("文件自动清理：删除废弃草稿记录 postId={}, url={}", row.getPostId(), row.getUrl());
                // 同哈希无其他引用时连带删除对象
                Long refCount = fileMapper.selectCount(new LambdaQueryWrapper<com.li.socialplatform.pojo.entity.File>()
                        .eq(com.li.socialplatform.pojo.entity.File::getHash, row.getHash()));
                if (refCount <= 0 && !STATIC_OBJECT_WHITELIST.contains(key)) {
                    try {
                        fileStorageService.delete(key);
                    } catch (Exception e) {
                        log.error("文件自动清理：删除草稿对象失败 {}", key, e);
                    }
                }
            }
        }

        // ---- 3. 一致性告警：业务引用指向但对象缺失 ----
        Set<String> missingObjects = new HashSet<>(referencedKeys);
        missingObjects.removeAll(objects.keySet());
        missingObjects.removeIf(k -> k.isEmpty() || STATIC_OBJECT_WHITELIST.contains(k));
        if (!missingObjects.isEmpty()) {
            log.warn("文件自动清理：{} 条业务引用的对象在桶中缺失（需人工核查）：{}",
                    missingObjects.size(), missingObjects);
        }

        log.info("文件自动清理完成：桶对象 {} 个，业务引用 {} 个（file 记录 {} 条），删除孤儿对象 {} 个，删除废弃草稿记录 {} 条",
                objects.size(), referencedKeys.size(), allRows.size(), orphanDeleted, draftDeleted);
    }

    /** 相对 URL（/a/b.jpg）或完整 URL（IMG_BASE_URL + /a/b.jpg）→ 对象名；非本站 URL（外部图床等）返回空串 */
    private String toKey(String url) {
        if (url == null || url.isEmpty() || url.contains("..")) {
            return "";
        }
        if (url.startsWith(systemConstants.baseUrl)) {
            url = url.substring(systemConstants.baseUrl.length());
        }
        if (!url.startsWith("/")) {
            return ""; // 外部 URL（如 picsum.photos），与本站对象存储无关
        }
        return url.substring(1);
    }
}
