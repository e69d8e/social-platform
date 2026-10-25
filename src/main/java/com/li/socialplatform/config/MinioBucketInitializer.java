package com.li.socialplatform.config;

import com.li.socialplatform.common.properties.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MinIO 存储桶启动初始化：确保桶存在并设置为公共读（匿名可下载），供 Nginx 代理直出图片。
 *
 * @author e69d8e
 * @since 2026/10/25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinioBucketInitializer {

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
            log.info("MinIO 存储桶就绪：{}（endpoint: {}）", bucket, minioProperties.endpoint);
        } catch (Exception e) {
            log.error("MinIO 存储桶初始化失败，请检查 MINIO_ENDPOINT / MINIO_ACCESS_KEY / MINIO_SECRET_KEY 配置及 MinIO 服务状态", e);
        }
    }

    /** 生成允许匿名 GetObject 的桶策略（等效 mc anonymous set download） */
    private String publicReadPolicy(String bucket) {
        return """
                {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},"Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/*"]}]}\
                """.formatted(bucket);
    }
}
