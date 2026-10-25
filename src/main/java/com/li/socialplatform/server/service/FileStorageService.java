package com.li.socialplatform.server.service;

import java.util.Date;
import java.util.List;

/**
 * 文件存储服务抽象（当前实现为 MinIO 对象存储）
 *
 * @author e69d8e
 * @since 2026/10/25
 */
public interface FileStorageService {

    /**
     * 上传文件
     *
     * @param objectKey   对象名（不含桶名，形如 a/b/xxx.jpg）
     * @param bytes       文件内容
     * @param contentType 文件 MIME 类型，为 null 时按二进制流处理
     */
    void upload(String objectKey, byte[] bytes, String contentType);

    /**
     * 删除对象（对象不存在时视为成功，保持幂等）
     *
     * @param objectKey 对象名
     */
    void delete(String objectKey);

    /**
     * 列举桶内全部对象（对象名 + 最后修改时间），供对账清理任务使用
     */
    List<StorageObjectInfo> listAllObjects();

    /** 存储对象元信息 */
    record StorageObjectInfo(String objectKey, Date lastModified) {
    }
}

