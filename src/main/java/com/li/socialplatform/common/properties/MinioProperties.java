package com.li.socialplatform.common.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MinIO 对象存储连接配置
 *
 * @author e69d8e
 * @since 2026/10/25
 */
@Data
@Component
@ConfigurationProperties(prefix = "social-platform.minio")
public class MinioProperties {
    /** MinIO 服务地址（本地开发 http://127.0.0.1:9000，容器内 http://sp-minio:9000） */
    public String endpoint;

    public String accessKey;

    public String secretKey;

    /** 图片存储桶名（需与 nginx /imgs 代理配置中的桶名保持一致） */
    public String bucket;

    /** 是否启用文件自动清理任务（孤儿对象 + 废弃草稿记录） */
    public boolean cleanupEnabled;

    /** 清理宽限期（小时）：最近修改时间在此之内的对象/记录不清理，避免误伤上传与发布间隙中的文件 */
    public int cleanupGraceHours;
}
