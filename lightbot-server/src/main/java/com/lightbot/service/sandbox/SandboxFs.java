package com.lightbot.service.sandbox;

import com.lightbot.util.MinioUtil;
import com.lightbot.util.SandboxPathValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 沙盒虚拟文件系统
 * <p>统一 Skill 只读区 + 工作区读写区的文件操作，复用 {@link SandboxPathValidator} 做路径安全校验。</p>
 *
 * @author finch
 * @since 2026-06-24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SandboxFs {

    private final MinioUtil minioUtil;

    /**
     * 读取文件
     *
     * @param path 沙盒路径
     * @return 文件内容
     */
    public String readFile(SandboxPath path) {
        String minioPath = path.toMinioPath();
        SandboxPathValidator.checkReadable(minioPath);
        byte[] bytes = minioUtil.downloadBytes(minioPath);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 写入文件（仅工作区）
     *
     * @param path    沙盒路径
     * @param content 文件内容
     */
    public void writeFile(SandboxPath path, String content) {
        if (path.type() == SandboxPath.PathType.SKILL) {
            throw new UnsupportedOperationException("Skill 目录为只读，不可写入");
        }
        String minioPath = path.toMinioPath();
        SandboxPathValidator.checkWritable(minioPath);
        minioUtil.uploadString(content, minioPath, "application/octet-stream");
    }

    /**
     * 列出目录文件
     *
     * @param path 沙盒路径（目录）
     * @return 文件路径列表
     */
    public List<String> listFiles(SandboxPath path) {
        String minioPath = path.toMinioPath();
        SandboxPathValidator.checkReadable(minioPath);
        return minioUtil.listObjects(minioPath);
    }

    /**
     * 删除文件（仅工作区）
     *
     * @param path 沙盒路径
     */
    public void deleteFile(SandboxPath path) {
        if (path.type() == SandboxPath.PathType.SKILL) {
            throw new UnsupportedOperationException("Skill 目录为只读，不可删除");
        }
        String minioPath = path.toMinioPath();
        SandboxPathValidator.checkWritable(minioPath);
        minioUtil.delete(minioPath);
    }

    /**
     * 检查文件是否存在
     *
     * @param path 沙盒路径
     * @return 是否存在
     */
    public boolean fileExists(SandboxPath path) {
        String minioPath = path.toMinioPath();
        SandboxPathValidator.checkReadable(minioPath);
        return minioUtil.exists(minioPath);
    }
}
