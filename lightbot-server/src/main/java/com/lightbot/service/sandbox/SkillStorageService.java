package com.lightbot.service.sandbox;

import com.lightbot.common.BizException;
import com.lightbot.dto.SkillFileTreeNode;
import com.lightbot.dto.SkillImportPreview;
import com.lightbot.enums.ErrorCode;
import com.lightbot.model.SkillMetadata;
import com.lightbot.util.MinioUtil;
import com.lightbot.util.SandboxPathValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Skill 文件存储服务
 * <p>封装 MinIO 上 skills/ 目录的读写、SKILL.md frontmatter 解析、ZIP 导入导出。</p>
 *
 * @author finch
 * @since 2026-06-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillStorageService {

    private final MinioUtil minioUtil;

    private static final String SKILLS_ROOT = "skills/";
    private static final String DRAFTS_ROOT = "skill_drafts/";
    private static final String SKILL_MD = "SKILL.md";

    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile("^---\\n(.*?)\\n---\\n(.*)$", Pattern.DOTALL);

    // ==================== SKILL.md 读写 ====================

    /**
     * 读取 SKILL.md 内容
     *
     * @param slug Skill 标识
     * @return SKILL.md 全文
     */
    public String getSkillMarkdown(String slug) {
        String path = SKILLS_ROOT + slug + "/" + SKILL_MD;
        if (!minioUtil.exists(path)) {
            throw new BizException(ErrorCode.SKILL_FILE_NOT_FOUND);
        }
        byte[] bytes = minioUtil.downloadBytes(path);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 写入 SKILL.md 内容
     *
     * @param slug    Skill 标识
     * @param content SKILL.md 全文
     */
    public void writeSkillMarkdown(String slug, String content) {
        String path = SKILLS_ROOT + slug + "/" + SKILL_MD;
        minioUtil.uploadString(content, path, "text/markdown");
        log.info("[SkillStorage] 写入 SKILL.md: path={}", path);
    }

    /**
     * 检查 SKILL.md 是否存在
     */
    public boolean skillMarkdownExists(String slug) {
        return minioUtil.exists(SKILLS_ROOT + slug + "/" + SKILL_MD);
    }

    /**
     * 删除整个 Skill 目录
     */
    public void deleteSkillDirectory(String slug) {
        String prefix = SKILLS_ROOT + slug + "/";
        List<String> objects = minioUtil.listObjects(prefix);
        for (String obj : objects) {
            minioUtil.delete(obj);
        }
        log.info("[SkillStorage] 删除 Skill 目录: prefix={}, 文件数={}", prefix, objects.size());
    }

    /**
     * 列出 Skill 目录下的文件（去掉前缀，只返回相对路径）
     */
    public List<String> listSkillFiles(String slug) {
        String prefix = SKILLS_ROOT + slug + "/";
        List<String> objects = minioUtil.listObjects(prefix);
        return objects.stream()
                .map(obj -> obj.substring(prefix.length()))
                .toList();
    }

    /**
     * 构建 Skill 文件树结构
     *
     * @param slug Skill 标识
     * @return 文件树根节点列表
     */
    public List<SkillFileTreeNode> buildFileTree(String slug) {
        List<String> files = listSkillFiles(slug);
        return buildTreeFromPaths(files);
    }

    /**
     * 读取 Skill 目录下任意文件内容
     *
     * @param slug         Skill 标识
     * @param relativePath 相对路径
     * @return 文件字节数组
     */
    public byte[] readFile(String slug, String relativePath) {
        String fullPath = SKILLS_ROOT + slug + "/" + relativePath;
        if (!minioUtil.exists(fullPath)) {
            throw new BizException(ErrorCode.SKILL_FILE_NOT_FOUND);
        }
        return minioUtil.downloadBytes(fullPath);
    }

    /**
     * 写入 Skill 目录下任意文件
     *
     * @param slug         Skill 标识
     * @param relativePath 相对路径
     * @param content      文件内容
     */
    public void writeFile(String slug, String relativePath, String content) {
        String fullPath = SKILLS_ROOT + slug + "/" + relativePath;
        String contentType = guessContentType(relativePath);
        minioUtil.uploadString(content, fullPath, contentType);
        log.info("[SkillStorage] 写入文件: path={}", fullPath);
    }

    /**
     * 删除 Skill 目录下任意文件或目录
     *
     * @param slug         Skill 标识
     * @param relativePath 相对路径
     */
    public void deleteFile(String slug, String relativePath) {
        String prefix = SKILLS_ROOT + slug + "/" + relativePath;
        // 如果是目录（以/结尾或匹配到子对象），批量删除
        if (relativePath.endsWith("/")) {
            List<String> objects = minioUtil.listObjects(prefix);
            for (String obj : objects) {
                minioUtil.delete(obj);
            }
            log.info("[SkillStorage] 删除目录: prefix={}, 文件数={}", prefix, objects.size());
        } else {
            if (!minioUtil.exists(prefix)) {
                throw new BizException(ErrorCode.SKILL_FILE_NOT_FOUND);
            }
            minioUtil.delete(prefix);
            log.info("[SkillStorage] 删除文件: path={}", prefix);
        }
    }

    /**
     * 校验文件路径安全性：禁止 .. 并确保在 Skill 目录范围内
     *
     * @param relativePath 相对路径
     */
    public void validateFilePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "文件路径不能为空");
        }
        // 标准化路径分隔符
        String normalized = relativePath.replace("\\", "/");
        if (normalized.contains("..")) {
            throw new BizException(ErrorCode.SANDBOX_PATH_VIOLATION, "路径不允许包含 ..");
        }
        if (normalized.startsWith("/") || normalized.startsWith("skills/")) {
            throw new BizException(ErrorCode.SANDBOX_PATH_VIOLATION, "路径必须为相对路径");
        }
    }

    /**
     * 从扁平路径列表构建树形结构
     */
    private List<SkillFileTreeNode> buildTreeFromPaths(List<String> paths) {
        // 使用 LinkedHashMap 保持插入顺序
        Map<String, SkillFileTreeNode> rootMap = new LinkedHashMap<>();

        for (String path : paths) {
            String[] parts = path.split("/");
            Map<String, SkillFileTreeNode> currentLevel = rootMap;

            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                boolean isLeaf = (i == parts.length - 1);

                SkillFileTreeNode node = currentLevel.get(part);
                if (node == null) {
                    node = new SkillFileTreeNode();
                    node.setName(part);
                    if (isLeaf) {
                        // 完整相对路径
                        node.setPath(path);
                        node.setDir(false);
                    } else {
                        // 中间目录：path 为到此层级的前缀
                        node.setPath(String.join("/", java.util.Arrays.copyOfRange(parts, 0, i + 1)) + "/");
                        node.setDir(true);
                        node.setChildren(new ArrayList<>());
                    }
                    currentLevel.put(part, node);
                }

                if (!isLeaf) {
                    if (node.getChildren() == null) {
                        node.setChildren(new ArrayList<>());
                    }
                    // 构建子层级索引
                    Map<String, SkillFileTreeNode> childMap = new LinkedHashMap<>();
                    for (SkillFileTreeNode child : node.getChildren()) {
                        childMap.put(child.getName(), child);
                    }
                    currentLevel = childMap;
                }
            }
        }

        return new ArrayList<>(rootMap.values());
    }

    /**
     * 根据文件扩展名猜测 Content-Type
     */
    private String guessContentType(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".md")) return "text/markdown";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) return "text/yaml";
        if (lower.endsWith(".xml")) return "application/xml";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".js")) return "application/javascript";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".pdf")) return "application/pdf";
        return "application/octet-stream";
    }

    // ==================== Frontmatter 解析 ====================

    /**
     * 解析 SKILL.md 为 SkillMetadata
     * <p>frontmatter 用 --- 分隔，SnakeYAML 安全解析。无 frontmatter 时降级：整个内容作为 promptTemplate。</p>
     */
    public SkillMetadata parseSkillMarkdown(String content) {
        if (content == null || content.isBlank()) {
            return SkillMetadata.builder().promptTemplate("").build();
        }

        String[] parts = splitFrontmatter(content);
        if (parts == null) {
            // 无 frontmatter，降级
            return SkillMetadata.builder().promptTemplate(content.trim()).build();
        }

        String yamlStr = parts[0];
        String body = parts[1];

        Map<String, Object> data = parseFrontmatter(yamlStr);
        if (data == null) {
            // YAML 解析失败，降级
            return SkillMetadata.builder().promptTemplate(content.trim()).build();
        }

        return SkillMetadata.builder()
                .slug(getString(data, "slug"))
                .name(getString(data, "name"))
                .description(getString(data, "description"))
                .version(getString(data, "version"))
                .toolDependencies(getStringList(data, "tool_dependencies"))
                .mcpDependencies(getStringList(data, "mcp_dependencies"))
                .skillDependencies(getStringList(data, "skill_dependencies"))
                .promptTemplate(body.trim())
                .build();
    }

    /**
     * 从 SkillMetadata 构建 SKILL.md 内容
     */
    public String buildSkillMarkdown(SkillMetadata metadata) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        appendYamlField(sb, "name", metadata.getName());
        appendYamlField(sb, "slug", metadata.getSlug());
        appendYamlField(sb, "description", metadata.getDescription());
        appendYamlField(sb, "version", metadata.getVersion() != null ? metadata.getVersion() : "1.0.0");
        appendYamlList(sb, "tool_dependencies", metadata.getToolDependencies());
        appendYamlList(sb, "mcp_dependencies", metadata.getMcpDependencies());
        appendYamlList(sb, "skill_dependencies", metadata.getSkillDependencies());
        sb.append("---\n\n");
        if (metadata.getPromptTemplate() != null) {
            sb.append(metadata.getPromptTemplate());
        }
        return sb.toString();
    }

    // ==================== ZIP 导入（两阶段） ====================

    /**
     * 阶段一：解压 ZIP 到草稿目录，解析 SKILL.md 返回预览
     *
     * @param draftId  草稿 ID
     * @param zipStream ZIP 输入流
     * @return 导入预览
     */
    public SkillImportPreview stageDraft(String draftId, InputStream zipStream) {
        String draftPrefix = DRAFTS_ROOT + draftId + "/";
        String skillMdContent = null;
        List<String> fileNames = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = entry.getName();
                // 标准化：去掉顶层目录前缀（如 my-skill/SKILL.md → SKILL.md）
                String normalized = normalizeZipEntryName(entryName);
                fileNames.add(normalized);

                // 读取内容
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                zis.transferTo(baos);
                byte[] content = baos.toByteArray();

                // 上传到草稿目录
                String destPath = draftPrefix + normalized;
                minioUtil.upload(new ByteArrayInputStream(content), destPath, content.length, "application/octet-stream");

                // 捕获 SKILL.md 内容
                if (SKILL_MD.equals(normalized)) {
                    skillMdContent = new String(content, StandardCharsets.UTF_8);
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            cleanupDraft(draftId);
            throw new BizException(ErrorCode.SKILL_ZIP_INVALID, e.getMessage());
        }

        if (skillMdContent == null) {
            cleanupDraft(draftId);
            throw new BizException(ErrorCode.SKILL_ZIP_INVALID, "ZIP 中缺少 SKILL.md");
        }

        SkillMetadata metadata = parseSkillMarkdown(skillMdContent);

        SkillImportPreview preview = new SkillImportPreview();
        preview.setDraftId(draftId);
        preview.setSlug(metadata.getSlug());
        preview.setName(metadata.getName());
        preview.setDescription(metadata.getDescription());
        preview.setVersion(metadata.getVersion());
        preview.setToolDependencies(metadata.getToolDependencies());
        preview.setSkillDependencies(metadata.getSkillDependencies());
        preview.setFileNames(fileNames);
        return preview;
    }

    /**
     * 阶段二：将草稿移动到正式目录
     *
     * @param draftId   草稿 ID
     * @param targetSlug 目标 slug（可为 null，使用 SKILL.md 中的 slug）
     * @return 最终 slug
     */
    public String commitDraft(String draftId, String targetSlug) {
        String draftPrefix = DRAFTS_ROOT + draftId + "/";
        List<String> objects = minioUtil.listObjects(draftPrefix);
        if (objects.isEmpty()) {
            throw new BizException(ErrorCode.SKILL_IMPORT_FAILED, "草稿目录为空");
        }

        // 如果未指定 targetSlug，从草稿中的 SKILL.md 解析
        String finalSlug = targetSlug;
        if (finalSlug == null || finalSlug.isBlank()) {
            String skillMdPath = draftPrefix + SKILL_MD;
            if (minioUtil.exists(skillMdPath)) {
                byte[] bytes = minioUtil.downloadBytes(skillMdPath);
                SkillMetadata metadata = parseSkillMarkdown(new String(bytes, StandardCharsets.UTF_8));
                finalSlug = metadata.getSlug();
            }
            if (finalSlug == null || finalSlug.isBlank()) {
                finalSlug = draftId;
            }
        }

        // 复制草稿对象到正式目录
        String targetPrefix = SKILLS_ROOT + finalSlug + "/";
        for (String srcPath : objects) {
            String relativePath = srcPath.substring(draftPrefix.length());
            String destPath = targetPrefix + relativePath;
            minioUtil.copyObject(srcPath, destPath);
        }

        // 清理草稿
        cleanupDraft(draftId);

        log.info("[SkillStorage] 草稿提交完成: draftId={}, slug={}, 文件数={}", draftId, finalSlug, objects.size());
        return finalSlug;
    }

    /**
     * 上传文件到草稿目录（供 GitHubSkillService 使用）
     *
     * @param destPath 完整目标路径（如 skill_drafts/{draftId}/{slug}/SKILL.md）
     * @param content  文件内容
     */
    public void uploadDraftFile(String destPath, byte[] content) {
        minioUtil.upload(new ByteArrayInputStream(content), destPath, content.length, "application/octet-stream");
    }

    /**
     * 将草稿中指定 slug 的文件复制到正式目录（远程安装专用）
     *
     * @param draftId 草稿 ID
     * @param slug    Skill slug（草稿中的子目录名）
     * @return 最终 slug
     */
    public String commitDraftForSlug(String draftId, String slug) {
        String draftSkillPrefix = DRAFTS_ROOT + draftId + "/" + slug + "/";
        List<String> objects = minioUtil.listObjects(draftSkillPrefix);
        if (objects.isEmpty()) {
            throw new BizException(ErrorCode.SKILL_IMPORT_FAILED, "草稿中未找到 Skill: " + slug);
        }

        String targetPrefix = SKILLS_ROOT + slug + "/";
        for (String srcPath : objects) {
            String relativePath = srcPath.substring(draftSkillPrefix.length());
            String destPath = targetPrefix + relativePath;
            minioUtil.copyObject(srcPath, destPath);
        }

        // 清理该 slug 的草稿子目录
        for (String obj : objects) {
            minioUtil.delete(obj);
        }

        log.info("[SkillStorage] 远程草稿提交: draftId={}, slug={}, 文件数={}", draftId, slug, objects.size());
        return slug;
    }

    /**
     * 清理草稿目录
     */
    public void cleanupDraft(String draftId) {
        String draftPrefix = DRAFTS_ROOT + draftId + "/";
        try {
            List<String> objects = minioUtil.listObjects(draftPrefix);
            for (String obj : objects) {
                minioUtil.delete(obj);
            }
        } catch (Exception e) {
            log.warn("[SkillStorage] 清理草稿失败: draftId={}", draftId, e);
        }
    }

    // ==================== ZIP 导出 ====================

    /**
     * 将 Skill 目录导出为 ZIP
     *
     * @param slug Skill 标识
     * @return ZIP 字节数组
     */
    public byte[] exportSkillZip(String slug) {
        String prefix = SKILLS_ROOT + slug + "/";
        List<String> objects = minioUtil.listObjects(prefix);
        if (objects.isEmpty()) {
            throw new BizException(ErrorCode.SKILL_FILE_NOT_FOUND);
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (String objPath : objects) {
                String relativePath = objPath.substring(prefix.length());
                byte[] content = minioUtil.downloadBytes(objPath);
                zos.putNextEntry(new ZipEntry(slug + "/" + relativePath));
                zos.write(content);
                zos.closeEntry();
            }
            zos.finish();
            return baos.toByteArray();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.SKILL_IMPORT_FAILED, "导出失败: " + e.getMessage());
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 从 SKILL.md 中提取 frontmatter 和 body
     *
     * @return [yamlStr, body] 或 null（无 frontmatter）
     */
    private String[] splitFrontmatter(String content) {
        Matcher m = FRONTMATTER_PATTERN.matcher(content);
        if (!m.find()) {
            return null;
        }
        return new String[]{m.group(1), m.group(2)};
    }

    /**
     * 安全解析 YAML frontmatter
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseFrontmatter(String yamlStr) {
        try {
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Object result = yaml.load(yamlStr);
            if (result instanceof Map) {
                return (Map<String, Object>) result;
            }
            return null;
        } catch (Exception e) {
            log.warn("[SkillStorage] YAML 解析失败: {}", e.getMessage());
            return null;
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private List<String> getStringList(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object item : (List<?>) val) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
            return result;
        }
        return List.of();
    }

    /**
     * 标准化 ZIP 条目名：去掉顶层目录前缀
     * 如 "my-skill/SKILL.md" → "SKILL.md"
     */
    private String normalizeZipEntryName(String entryName) {
        String normalized = entryName.replace("\\", "/");
        // 去掉顶层目录
        int slashIdx = normalized.indexOf('/');
        if (slashIdx >= 0 && slashIdx < normalized.length() - 1) {
            // 检查是否只有一个顶层目录
            String rest = normalized.substring(slashIdx + 1);
            if (!rest.contains("/")) {
                return rest;
            }
        }
        return normalized;
    }

    private void appendYamlField(StringBuilder sb, String key, String value) {
        if (value != null) {
            sb.append(key).append(": ").append(value).append("\n");
        }
    }

    private void appendYamlList(StringBuilder sb, String key, List<String> values) {
        if (values == null || values.isEmpty()) {
            sb.append(key).append(": []\n");
        } else {
            sb.append(key).append(":\n");
            for (String v : values) {
                sb.append("  - ").append(v).append("\n");
            }
        }
    }
}
