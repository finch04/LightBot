package com.lightbot.util;

import com.lightbot.common.BizException;
import com.lightbot.enums.ErrorCode;
import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

    private static final long MAX_BYTES_FOR_IN_MEMORY = 10 * 1024 * 1024L;

    private final MinioClient minioClient;
    private final String endpoint;

    @Value("${minio.bucket}")
    private String bucket;

    private volatile boolean bucketEnsured = false;

    public MinioUtil(
            @Value("${minio.endpoint}") String endpoint,
            @Value("${minio.access-key}") String accessKey,
            @Value("${minio.secret-key}") String secretKey) {
        this.endpoint = endpoint;
        // 1. 配置 OkHttp 连接池
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(5))
                .writeTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(30))
                .connectionPool(new ConnectionPool(32, 5, TimeUnit.MINUTES))
                .build();
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .httpClient(httpClient)
                .build();
    }

    /**
     * 启动时确保 Bucket 存在（仅调用一次）
     */
    @PostConstruct
    public void init() {
        ensureBucketOnce();
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
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(filePath)
                    .stream(inputStream, size, 10 * 1024 * 1024)
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
     * 下载文件（返回 InputStream，调用方需自行关闭）
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
     * 流式下载文件（返回 InputStream，适用于大文件，调用方需自行关闭）
     *
     * @param filePath 文件路径
     * @return 输入流
     */
    public InputStream downloadStream(String filePath) {
        return download(filePath);
    }

    /**
     * 下载文件为字节数组（仅适用于小文件 &lt;10MB）
     *
     * @param filePath 文件路径
     * @return 字节数组
     */
    public byte[] downloadBytes(String filePath) {
        try {
            // 1. 检查文件大小，超过 10MB 禁止全量读入内存
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder().bucket(bucket).object(filePath).build());
            if (stat.size() > MAX_BYTES_FOR_IN_MEMORY) {
                throw new BizException(ErrorCode.FILE_TOO_LARGE_FOR_MEMORY);
            }
            try (InputStream in = download(filePath)) {
                return in.readAllBytes();
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[MinIO] 下载字节失败, path={}", filePath, e);
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
     * 获取文件永久公开URL（需 Bucket 开启公开读权限）
     * <p>格式：{endpoint}/{bucket}/{filePath}</p>
     *
     * @param filePath 文件路径
     * @return 公开访问URL
     */
    public String getPublicUrl(String filePath) {
        String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        return base + "/" + bucket + "/" + filePath;
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

    /**
     * 列出指定前缀下的所有对象名
     *
     * @param prefix 路径前缀
     * @return 对象名列表
     */
    public java.util.List<String> listObjects(String prefix) {
        try {
            java.util.List<String> result = new java.util.ArrayList<>();
            var items = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(bucket).prefix(prefix).recursive(true).build());
            for (var item : items) {
                io.minio.messages.Item obj = item.get();
                if (!obj.isDir()) {
                    result.add(obj.objectName());
                }
            }
            return result;
        } catch (Exception e) {
            log.error("[MinIO] 列举对象失败, prefix={}", prefix, e);
            throw new BizException(ErrorCode.FILE_DOWNLOAD_FAILED);
        }
    }

    /**
     * 非递归列出指定目录前缀下的「直接子条目」（模拟目录结构）。
     * <p>MinIO 无真实目录概念，这里通过 {@code recursive=false} 列举并按 {@code /} 聚合。</p>
     *
     * @param directoryPrefix 目录前缀，必须以 {@code /} 结尾（如 {@code sessions/123/inputs/}）
     * @return 直接子条目列表（包含目录与文件）
     */
    public java.util.List<MinioDirEntry> listDirectoryEntries(String directoryPrefix) {
        try {
            java.util.List<MinioDirEntry> result = new java.util.ArrayList<>();
            var items = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(bucket).prefix(directoryPrefix).recursive(false).build());
            for (var item : items) {
                io.minio.messages.Item obj = item.get();
                String objectName = obj.objectName();
                boolean isDir = obj.isDir();
                // 目录条目：objectName 形如 sessions/123/inputs/，name 取最后一段非空
                // 文件条目：objectName 形如 sessions/123/inputs/abc.png
                String name = extractLastSegment(objectName, isDir);
                if (name == null || name.isEmpty()) {
                    continue;
                }
                MinioDirEntry entry = new MinioDirEntry();
                entry.name = name;
                entry.objectName = isDir ? stripTrailingSlash(objectName) : objectName;
                entry.directory = isDir;
                entry.size = isDir ? 0L : obj.size();
                entry.lastModified = isDir ? null : java.time.OffsetDateTime.now().toString();
                try {
                    if (!isDir) {
                        entry.lastModified = obj.lastModified() != null ? obj.lastModified().toString() : null;
                    }
                } catch (Exception ignored) {
                }
                result.add(entry);
            }
            return result;
        } catch (Exception e) {
            log.error("[MinIO] 列举目录失败, prefix={}", directoryPrefix, e);
            throw new BizException(ErrorCode.FILE_DOWNLOAD_FAILED);
        }
    }

    private static String extractLastSegment(String objectName, boolean isDir) {
        String s = objectName;
        if (isDir) {
            s = stripTrailingSlash(s);
        }
        int idx = s.lastIndexOf('/');
        return idx >= 0 ? s.substring(idx + 1) : s;
    }

    private static String stripTrailingSlash(String s) {
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /** MinIO 目录条目（非递归列举结果） */
    public static class MinioDirEntry {
        public String name;
        public String objectName;
        public boolean directory;
        public long size;
        public String lastModified;
    }

    /**
     * 删除指定前缀下的所有对象（用于会话工作区清理）
     *
     * @param prefix 路径前缀
     * @return 删除的对象数量
     */
    public int deleteByPrefix(String prefix) {
        try {
            java.util.List<String> objects = listObjects(prefix);
            if (objects.isEmpty()) {
                return 0;
            }
            for (String obj : objects) {
                minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(obj).build());
            }
            log.info("[MinIO] 批量删除完成, prefix={}, count={}", prefix, objects.size());
            return objects.size();
        } catch (Exception e) {
            log.error("[MinIO] 批量删除失败, prefix={}", prefix, e);
            throw new BizException(ErrorCode.FILE_DOWNLOAD_FAILED);
        }
    }

    /**
     * 同 bucket 内复制对象
     *
     * @param sourcePath 源路径
     * @param destPath   目标路径
     */
    public void copyObject(String sourcePath, String destPath) {
        try {
            minioClient.copyObject(CopyObjectArgs.builder()
                    .bucket(bucket)
                    .object(destPath)
                    .source(CopySource.builder().bucket(bucket).object(sourcePath).build())
                    .build());
        } catch (Exception e) {
            log.error("[MinIO] 复制对象失败, src={}, dest={}", sourcePath, destPath, e);
            throw new BizException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    /**
     * 判断对象是否存在
     *
     * @param filePath 文件路径
     * @return 是否存在
     */
    public boolean exists(String filePath) {
        try {
            minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(filePath).build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 健康检查：验证 MinIO 连接和 Bucket 是否可用
     *
     * @return true=可用
     */
    public boolean checkHealth() {
        try {
            return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        } catch (Exception e) {
            log.error("[MinIO] 健康检查失败", e);
            return false;
        }
    }

    /**
     * 获取对象元数据
     *
     * @param filePath 文件路径
     * @return StatObjectResponse
     */
    public StatObjectResponse statObject(String filePath) {
        try {
            return minioClient.statObject(
                    StatObjectArgs.builder().bucket(bucket).object(filePath).build());
        } catch (Exception e) {
            log.error("[MinIO] 获取对象元数据失败, path={}", filePath, e);
            throw new BizException(ErrorCode.FILE_DOWNLOAD_FAILED);
        }
    }

    /**
     * 删除旧头像：兼容完整URL和相对路径
     *
     * @param avatar 头像URL或相对路径
     */
    public void deleteAvatar(String avatar) {
        if (avatar == null || avatar.isEmpty()) return;
        // 兼容旧数据（相对路径）和新数据（完整URL）
        String path = avatar.contains("/lightbot/") ? avatar.substring(avatar.indexOf("/lightbot/") + 10) : avatar;
        try {
            delete(path);
        } catch (Exception e) {
            log.warn("[MinIO] 删除头像失败: path={}, error={}", path, e.getMessage());
        }
    }

    /**
     * 双重检查锁确保 Bucket 仅在启动时创建一次
     */
    private void ensureBucketOnce() {
        if (bucketEnsured) return;
        synchronized (this) {
            if (bucketEnsured) return;
            try {
                boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
                if (!exists) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
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
                bucketEnsured = true;
            } catch (Exception e) {
                log.error("[MinIO] 检查/创建Bucket失败", e);
            }
        }
    }
}
