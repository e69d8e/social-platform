package com.li.socialplatform.config;

import com.li.socialplatform.common.properties.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import io.minio.StatObjectArgs;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;

/**
 * MinIO 存储桶启动初始化：确保桶存在并设置为公共读（匿名可下载），供 Nginx 代理直出图片；
 * 桶内缺失系统静态图片（默认头像、站点 Logo）时从 classpath 补传，新环境部署无需手工迁移。
 *
 * @author e69d8e
 * @since 2026/10/25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinioBucketInitializer {

    /** 系统静态图片：桶内对象 key -> classpath 资源路径（与 FileCleanupTask 清理白名单对应） */
    private static final Map<String, String> STATIC_OBJECTS = Map.of(
            "avatar/default.jpg", "/static/imgs/avatar/default.jpg",
            "logo.png", "/static/imgs/logo.png"
    );

    private final MinioClient minioClient;

    private final MinioProperties minioProperties;

    /**
     * 校验失败仅记录错误日志，不阻塞应用启动；上传时会给出明确错误提示。
     */
    @PostConstruct
    public void initBucket() {
        try {
            String bucket = minioProperties.bucket;
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(bucket)
                    .config(publicReadPolicy(bucket))
                    .build());
            ensureStaticObjects(bucket);
            log.info("MinIO 存储桶就绪：{}（endpoint: {}）", bucket, minioProperties.endpoint);
        } catch (Exception e) {
            log.error("MinIO 存储桶初始化失败，请检查 MINIO_ENDPOINT / MINIO_ACCESS_KEY / MINIO_SECRET_KEY 配置及 MinIO 服务状态", e);
        }
    }

    /** 桶内缺失的系统静态图片从 classpath 补传（已存在则跳过，不覆盖） */
    private void ensureStaticObjects(String bucket) {
        for (Map.Entry<String, String> entry : STATIC_OBJECTS.entrySet()) {
            try {
                minioClient.statObject(StatObjectArgs.builder()
                        .bucket(bucket)
                        .object(entry.getKey())
                        .build());
                continue; // 已存在，跳过
            } catch (Exception notFound) {
                // 对象缺失，走补传
            }
            try (InputStream in = getClass().getResourceAsStream(entry.getValue())) {
                if (in == null) {
                    log.warn("classpath 内置静态图片缺失，无法补传：{}", entry.getValue());
                    continue;
                }
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(entry.getKey())
                        .stream(in, -1, 10 * 1024 * 1024)
                        .build());
                log.info("已补传系统静态图片：{}", entry.getKey());
            } catch (Exception uploadEx) {
                log.warn("补传系统静态图片失败：{}", entry.getKey(), uploadEx);
            }
        }
    }

    /** 生成允许匿名 GetObject 的桶策略（等效 mc anonymous set download） */
    private String publicReadPolicy(String bucket) {
        return """
                {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},"Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/*"]}]}\
                """.formatted(bucket);
    }
}
