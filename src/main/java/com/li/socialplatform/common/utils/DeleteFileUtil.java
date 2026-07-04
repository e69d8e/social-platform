package com.li.socialplatform.common.utils;

import cn.hutool.core.io.FileUtil;
import com.li.socialplatform.common.properties.SystemConstants;
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

    private final SystemConstants systemConstants;

    public void deleteFile(String url) {
        if (url == null || !url.startsWith("/") || url.contains("..")) {
            return;
        }

        // 去掉开头的 /，防止 new File(parent, "/absolute") 忽略 parent 目录
        String relativePath = url.startsWith("/") ? url.substring(1) : url;
        java.io.File file = new java.io.File(systemConstants.imageUploadDir, relativePath);
        if (file.isDirectory()) {
            return;
        }

        // 验证最终路径确实在允许的目录内
        try {
            String canonicalPath = file.getCanonicalPath();
            String canonicalBase = new java.io.File(systemConstants.imageUploadDir).getCanonicalPath();
            if (!canonicalPath.startsWith(canonicalBase + java.io.File.separator)) {
                log.warn("路径遍历攻击被拦截: {}", url);
                return;
            }
        } catch (Exception e) {
            log.error("路径解析失败: {}", url, e);
            return;
        }

        FileUtil.del(file);
    }
}
