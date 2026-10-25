package com.li.socialplatform.server.service.impl;

import com.li.socialplatform.common.properties.MinioProperties;
import com.li.socialplatform.server.service.FileStorageService;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * MinIO 文件存储实现
 *
 * @author e69d8e
 * @since 2026/10/25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioFileStorageService implements FileStorageService {

    private final MinioClient minioClient;

    private final MinioProperties minioProperties;

    @Override
    public void upload(String objectKey, byte[] bytes, String contentType) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.bucket)
                    .object(objectKey)
                    .stream(in, bytes.length, -1)
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .build());
        } catch (Exception e) {
            log.error("MinIO 上传对象失败：{}", objectKey, e);
            throw new RuntimeException("文件上传到对象存储失败", e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioProperties.bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            log.error("MinIO 删除对象失败：{}", objectKey, e);
            throw new RuntimeException("从对象存储删除文件失败", e);
        }
    }

    @Override
    public List<StorageObjectInfo> listAllObjects() {
        List<StorageObjectInfo> result = new ArrayList<>();
        try {
            for (Result<Item> item : minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(minioProperties.bucket).recursive(true).build())) {
                Item it = item.get();
                ZonedDateTime modified = it.lastModified();
                result.add(new StorageObjectInfo(
                        it.objectName(),
                        modified == null ? null : Date.from(modified.toInstant())));
            }
        } catch (Exception e) {
            throw new RuntimeException("列举 MinIO 对象失败", e);
        }
        return result;
    }
}
