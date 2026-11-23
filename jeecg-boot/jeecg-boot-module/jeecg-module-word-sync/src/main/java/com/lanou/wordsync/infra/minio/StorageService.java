package com.lanou.wordsync.infra.minio;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * 对象存储服务：负责 Word 文件在 MinIO 中的上传、下载、删除。
 *
 * objectKey 约定：docs/{docId}/v{versionNo}/{fileName}
 */
@Service
public class StorageService {

    private final MinioClient minioClient;
    private final MinioProperties properties;

    public StorageService(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    /**
     * 启动时确认桶存在，不存在则自动创建。
     * 如果 MinIO 没启动，这里会抛异常，方便尽早发现环境问题。
     */
    @PostConstruct
    public void initBucket() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(properties.getBucket()).build());
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(properties.getBucket()).build());
            }
        } catch (Exception e) {
            throw new IllegalStateException("MinIO 初始化失败，请检查 minio.* 配置和容器状态", e);
        }
    }

    /**
     * 上传文件。
     *
     * @param objectKey   MinIO 对象 key，例如 docs/1/v1/周报.docx
     * @param content     文件内容
     * @param contentType MIME 类型
     */
    public void upload(String objectKey, byte[] content, String contentType) {
        try (InputStream in = new ByteArrayInputStream(content)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .stream(in, (long) content.length, -1L)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("上传文件到 MinIO 失败: " + objectKey, e);
        }
    }

    /**
     * 下载文件。
     */
    public byte[] download(String objectKey) {
        try (InputStream in = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(properties.getBucket())
                        .object(objectKey)
                        .build())) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("从 MinIO 下载文件失败: " + objectKey, e);
        }
    }

    /**
     * 删除文件（回滚/清理时可用）。
     */
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("删除 MinIO 文件失败: " + objectKey, e);
        }
    }
}
