package com.lightbot.util;

import com.lightbot.common.BizException;
import com.lightbot.enums.ErrorCode;
import io.minio.*;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 文件存储工具类
 * <p>封装 MinIO 客户端操作，供业务层调用</p>
 *
 * @author finch
 * @since 2026-05-19
 */
@Slf4j
@Component
public class MinioUtil {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    public MinioUtil(
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
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED);
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
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    /**
     * 上传字符串内容
     *
     * @param content     字符串内容
     * @param filePath    存储路径
     * @param contentType 内容类型
     * @return 文件路径
     */
    public String uploadString(String content, String filePath, String contentType) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            return upload(bais, filePath, bytes.length, contentType);
        } catch (Exception e) {
            log.error("[MinIO] 上传字符串失败, path={}", filePath, e);
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED);
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
            throw new BizException(ErrorCode.FILE_DOWNLOAD_FAILED);
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
        return getPresignedUrl(filePath, null);
    }

    /**
     * 获取文件预签名URL（7天有效，可指定Content-Type）
     *
     * @param filePath    文件路径
     * @param contentType 文件MIME类型，为null时不覆盖
     * @return 预签名URL
     */
    public String getPresignedUrl(String filePath, String contentType) {
        try {
            Map<String, String> extraParams = new java.util.HashMap<>();
            extraParams.put("response-content-disposition", "inline");
            if (contentType != null && !contentType.isBlank()) {
                extraParams.put("response-content-type", contentType);
            }
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(filePath)
                    .expiry(7, TimeUnit.DAYS)
                    .extraQueryParams(extraParams)
                    .build());
        } catch (Exception e) {
            log.error("[MinIO] 获取预签名URL失败, path={}", filePath, e);
            throw new BizException(ErrorCode.FILE_URL_FAILED);
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
                // 设置公开读取策略，允许匿名访问
                String policy = """
                        {
                            "Version": "2012-10-17",
                            "Statement": [
                                {
                                    "Effect": "Allow",
                                    "Principal": {"AWS": ["*"]},
                                    "Action": ["s3:GetObject"],
                                    "Resource": ["arn:aws:s3:::%s/*"]
                                }
                            ]
                        }
                        """.formatted(bucket);
                minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                        .bucket(bucket)
                        .config(policy)
                        .build());
                log.info("[MinIO] Bucket已创建并设置公开读取策略: {}", bucket);
            }
        } catch (Exception e) {
            log.error("[MinIO] 检查/创建Bucket失败", e);
        }
    }
}
