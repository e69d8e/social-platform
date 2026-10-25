package com.li.socialplatform.common.utils;

import com.li.socialplatform.server.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


/**
 * @author e69d8e
 * @since 2026/05/22 20:59
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeleteFileUtil {

    private final FileStorageService fileStorageService;

    /**
     * 根据文件相对 URL（形如 /a/b/xxx.jpg）删除 MinIO 存储对象。
     * 删除失败仅记录日志，不中断业务流程（孤儿对象可由存储侧生命周期策略兜底）。
     */
    public void deleteFile(String url) {
        if (url == null || !url.startsWith("/") || url.contains("..")) {
            return;
        }

        String objectKey = url.substring(1);
        try {
            fileStorageService.delete(objectKey);
        } catch (Exception e) {
            log.error("删除存储对象失败：{}", objectKey, e);
        }
    }
}
