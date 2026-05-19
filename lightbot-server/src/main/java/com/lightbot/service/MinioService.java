package com.lightbot.service;

import io.minio.*;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 文件存储服务
 *
 * @author finch
 * @since 2026-05-19
 */
@Slf4j
@Service
public class MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    public MinioService(
            @Value("${minio.endpoint}") String endpoint,
            @Value("${minio.access-key}") String accessKey,
            @Value("${minio.secret-key}") String secretKey) {
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    /**
     * 上传文件
     *
     * @param file     文件
     * @param filePath 存储路径，如 "knowledge/1/doc/xxx.md"
     * @return 文件路径
     */
    public String upload(MultipartFile file, String filePath) {
        try {
            ensureBucket();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(filePath)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            return filePath;
        } catch (Exception e) {
            log.error("[MinIO] 上传失败, path={}", filePath, e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    /**
     * 上传输入流
     *
     * @param inputStream 输入流
     * @param filePath    存储路径
     * @param size        文件大小
     * @param contentType 内容类型
     * @return 文件路径
     */
    public String upload(InputStream inputStream, String filePath, long size, String contentType) {
        try {
            ensureBucket();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(filePath)
                    .stream(inputStream, size, -1)
                    .contentType(contentType)
                    .build());
            return filePath;
        } catch (Exception e) {
            log.error("[MinIO] 上传失败, path={}", filePath, e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    /**
     * 下载文件
     *
     * @param filePath 文件路径
     * @return 输入流
     */
    public InputStream download(String filePath) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(filePath)
                    .build());
        } catch (Exception e) {
            log.error("[MinIO] 下载失败, path={}", filePath, e);
            throw new RuntimeException("文件下载失败", e);
        }
    }

    /**
     * 删除文件
     *
     * @param filePath 文件路径
     */
    public void delete(String filePath) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(filePath)
                    .build());
        } catch (Exception e) {
            log.error("[MinIO] 删除失败, path={}", filePath, e);
        }
    }

    /**
     * 获取文件预签名URL（7天有效）
     *
     * @param filePath 文件路径
     * @return 预签名URL
     */
    public String getPresignedUrl(String filePath) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(filePath)
                    .expiry(7, TimeUnit.DAYS)
                    .build());
        } catch (Exception e) {
            log.error("[MinIO] 获取预签名URL失败, path={}", filePath, e);
            throw new RuntimeException("获取文件URL失败", e);
        }
    }

    /**
     * 生成文件存储路径
     *
     * @param knowledgeId 知识库ID
     * @param fileName    文件名
     * @return 存储路径
     */
    public String generatePath(Long knowledgeId, String fileName) {
        String ext = "";
        int dotIdx = fileName.lastIndexOf('.');
        if (dotIdx > 0) {
            ext = fileName.substring(dotIdx);
        }
        return String.format("knowledge/%d/doc/%s%s", knowledgeId, UUID.randomUUID().toString().replace("-", ""), ext);
    }

    private void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            log.error("[MinIO] 检查/创建Bucket失败", e);
        }
    }
}
