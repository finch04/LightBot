package com.lightbot.service.sandbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.SkillImportPreview;
import com.lightbot.enums.ErrorCode;
import com.lightbot.model.SkillMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * GitHub 远程 Skill 安装服务
 * <p>通过 GitHub REST API 浏览仓库中的 Skill、下载 ZIP 并暂存为草稿。</p>
 *
 * @author finch
 * @since 2026-06-18
 */
@Slf4j
@Service
public class GitHubSkillService {

    private final SkillStorageService skillStorageService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${lightbot.github.token:}")
    private String githubToken;

    private static final String GITHUB_API = "https://api.github.com";
    private static final String SKILL_MD = "SKILL.md";
    private static final String MODELSCOPE_PREFIX = "https://modelscope.cn/skills/";

    public GitHubSkillService(SkillStorageService skillStorageService) {
        this.skillStorageService = skillStorageService;
    }

    /**
     * 判断是否为 ModelScope Skill 地址
     */
    public boolean isModelScopeUrl(String source) {
        return source != null && source.trim().startsWith(MODELSCOPE_PREFIX);
    }

    /**
     * 解析 source 字段为 [owner, repo, branch]
     *
     * @param source 支持格式：owner/repo、https://github.com/owner/repo、owner/repo@branch
     * @return [owner, repo, branch]，branch 可能为 null
     */
    public String[] parseSource(String source) {
        if (source == null || source.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "source 不能为空");
        }
        source = source.trim();

        // 去掉协议前缀
        String s = source;
        if (s.startsWith("https://github.com/")) {
            s = s.substring("https://github.com/".length());
        } else if (s.startsWith("http://github.com/")) {
            s = s.substring("http://github.com/".length());
        } else if (s.startsWith("github.com/")) {
            s = s.substring("github.com/".length());
        }

        // 去掉 .git 后缀和尾部斜杠
        s = s.replaceAll("\\.git$", "").replaceAll("/+$", "");

        // 解析 owner/repo@branch
        String branch = null;
        int atIdx = s.indexOf('@');
        if (atIdx > 0) {
            branch = s.substring(atIdx + 1);
            s = s.substring(0, atIdx);
        }

        String[] parts = s.split("/");
        if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "无法解析仓库地址，支持格式：owner/repo 或 GitHub URL");
        }

        return new String[]{parts[0], parts[1], branch};
    }

    /**
     * 列出远程仓库中所有包含 SKILL.md 的 Skill
     *
     * @param owner  仓库所有者
     * @param repo   仓库名
     * @param branch 分支名（null 时使用默认分支）
     * @return Skill 摘要列表（name + description）
     */
    public List<Map<String, String>> listRemoteSkills(String owner, String repo, String branch) {
        // 递归获取所有文件路径
        List<String[]> allFiles = new ArrayList<>();
        fetchContentsRecursive(owner, repo, "", branch, allFiles);

        // 过滤 SKILL.md
        List<String[]> skillMdFiles = new ArrayList<>();
        for (String[] file : allFiles) {
            if (SKILL_MD.equals(file[0]) || file[0].endsWith("/" + SKILL_MD)) {
                skillMdFiles.add(file);
            }
        }

        if (skillMdFiles.isEmpty()) {
            return List.of();
        }

        // 逐个下载 SKILL.md 解析元数据
        List<Map<String, String>> result = new ArrayList<>();
        for (String[] file : skillMdFiles) {
            String path = file[0];
            String slug = extractSlugFromPath(path);
            try {
                String content = getFileContent(owner, repo, path, branch);
                if (content != null) {
                    SkillMetadata metadata = skillStorageService.parseSkillMarkdown(content);
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("name", slug);
                    item.put("description", metadata.getDescription() != null ? metadata.getDescription() : "");
                    result.add(item);
                }
            } catch (Exception e) {
                log.warn("[GitHubSkill] 解析 SKILL.md 失败: path={}, error={}", path, e.getMessage());
            }
        }
        return result;
    }

    /**
     * 全局搜索 GitHub 上的 Skill（通过 Code Search API）
     *
     * @param keyword 搜索关键词
     * @return Skill 摘要列表（name + description + repo）
     */
    public List<Map<String, String>> searchRemoteSkills(String keyword) {
        if (githubToken == null || githubToken.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "全局搜索需要配置 GitHub Token，请在 lightbot.github.token 中设置");
        }
        String query = "filename:SKILL.md " + keyword;
        String url = GITHUB_API + "/search/code?q=" + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8)
                + "&per_page=20";

        List<Map<String, String>> result = new ArrayList<>();
        try {
            JsonNode node = githubGet(url);
            if (node == null || !node.has("items")) return result;

            for (JsonNode item : node.get("items")) {
                String filePath = item.has("path") ? item.get("path").asText() : "";
                JsonNode repoNode = item.get("repository");
                if (repoNode == null) continue;

                String repoFullName = repoNode.has("full_name") ? repoNode.get("full_name").asText() : "";
                String slug = extractSlugFromPath(filePath);

                // 尝试获取 SKILL.md 内容解析描述
                String[] parts = repoFullName.split("/");
                if (parts.length < 2) continue;
                try {
                    String content = getFileContent(parts[0], parts[1], filePath, null);
                    String description = "";
                    if (content != null) {
                        SkillMetadata metadata = skillStorageService.parseSkillMarkdown(content);
                        description = metadata.getDescription() != null ? metadata.getDescription() : "";
                    }
                    Map<String, String> entry = new LinkedHashMap<>();
                    entry.put("name", slug);
                    entry.put("description", description);
                    entry.put("repo", repoFullName);
                    result.add(entry);
                } catch (Exception e) {
                    log.warn("[GitHubSkill] 搜索结果解析失败: repo={}, path={}", repoFullName, filePath);
                }
            }
        } catch (Exception e) {
            log.warn("[GitHubSkill] 全局搜索失败: keyword={}, error={}", keyword, e.getMessage());
        }
        return result;
    }

    /**
     * 下载远程仓库 ZIP，提取选中的 Skill 暂存为草稿
     *
     * @param owner       仓库所有者
     * @param repo        仓库名
     * @param branch      分支名（null 时使用默认分支）
     * @param skillSlugs  选中的 Skill slug 列表
     * @return SkillImportPreview 列表（共享同一个 draftId）
     */
    public List<SkillImportPreview> prepareRemoteInstall(String owner, String repo, String branch,
                                                          List<String> skillSlugs) {
        String draftId = UUID.randomUUID().toString().replace("-", "");
        String effectiveBranch = branch != null ? branch : "main";
        Set<String> targetSlugs = new HashSet<>(skillSlugs);

        Path tempDir = null;
        try {
            // 1. 下载 ZIP 到临时目录
            tempDir = Files.createTempDirectory("github-skill-");
            String zipUrl = String.format("https://github.com/%s/%s/archive/refs/heads/%s.zip",
                    owner, repo, effectiveBranch);
            downloadAndExtractZip(zipUrl, tempDir);

            // 2. 找到解压后的根目录（GitHub ZIP 有 {repo}-{branch}/ 前缀）
            Path root = findExtractedRoot(tempDir);
            if (root == null) {
                throw new BizException(ErrorCode.SKILL_IMPORT_FAILED, "ZIP 解压后目录结构异常");
            }

            // 3. 扫描所有 SKILL.md，提取目标 Skill
            List<SkillImportPreview> previews = new ArrayList<>();
            Files.walk(root)
                    .filter(p -> SKILL_MD.equals(p.getFileName().toString()))
                    .forEach(skillMdPath -> {
                        Path skillDir = skillMdPath.getParent();
                        String slug = skillDir.getFileName().toString();

                        if (!targetSlugs.contains(slug)) {
                            return;
                        }

                        try {
                            byte[] content = Files.readAllBytes(skillMdPath);
                            String skillMdContent = new String(content, StandardCharsets.UTF_8);

                            // 收集该 Skill 目录下所有文件
                            List<String> fileNames = new ArrayList<>();
                            Files.walk(skillDir)
                                    .filter(Files::isRegularFile)
                                    .forEach(file -> {
                                        String relative = skillDir.relativize(file).toString()
                                                .replace("\\", "/");
                                        fileNames.add(relative);

                                        // 暂存到草稿目录
                                        String destPath = "skill_drafts/" + draftId + "/" + slug + "/" + relative;
                                        try (InputStream fis = Files.newInputStream(file)) {
                                            byte[] fileBytes = fis.readAllBytes();
                                            skillStorageService.uploadDraftFile(destPath, fileBytes);
                                        } catch (Exception e) {
                                            log.warn("[GitHubSkill] 暂存文件失败: {}", destPath, e);
                                        }
                                    });

                            SkillMetadata metadata = skillStorageService.parseSkillMarkdown(skillMdContent);
                            SkillImportPreview preview = new SkillImportPreview();
                            preview.setDraftId(draftId);
                            preview.setSlug(slug);
                            preview.setName(metadata.getName());
                            preview.setDescription(metadata.getDescription());
                            preview.setVersion(metadata.getVersion());
                            preview.setToolDependencies(metadata.getToolDependencies());
                            preview.setSkillDependencies(metadata.getSkillDependencies());
                            preview.setFileNames(fileNames);
                            previews.add(preview);
                        } catch (Exception e) {
                            log.warn("[GitHubSkill] 处理 Skill 目录失败: slug={}, error={}", slug, e.getMessage());
                        }
                    });

            if (previews.isEmpty()) {
                skillStorageService.cleanupDraft(draftId);
                throw new BizException(ErrorCode.SKILL_IMPORT_FAILED,
                        "未找到指定的 Skill: " + skillSlugs);
            }

            log.info("[GitHubSkill] 远程安装准备完成: source={}/{}, draftId={}, skills={}",
                    owner, repo, draftId, previews.stream().map(SkillImportPreview::getSlug).toList());
            return previews;

        } catch (BizException e) {
            skillStorageService.cleanupDraft(draftId);
            throw e;
        } catch (Exception e) {
            skillStorageService.cleanupDraft(draftId);
            throw new BizException(ErrorCode.SKILL_REMOTE_FETCH_FAILED, e.getMessage());
        } finally {
            // 清理临时目录
            if (tempDir != null) {
                try {
                    Files.walk(tempDir)
                            .sorted(java.util.Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                } catch (Exception ignored) {
                }
            }
        }
    }

    // ==================== 内部方法 ====================

    /** 构建带 Token 的请求头 */
    private HttpEntity<Void> githubEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.github.v3+json");
        if (githubToken != null && !githubToken.isBlank()) {
            headers.set("Authorization", "Bearer " + githubToken);
        }
        return new HttpEntity<>(headers);
    }

    /** GET 请求 GitHub API，自动携带 Token */
    private JsonNode githubGet(String url) {
        ResponseEntity<JsonNode> resp = restTemplate.exchange(url, HttpMethod.GET, githubEntity(), JsonNode.class);
        return resp.getBody();
    }

    /** 递归获取 GitHub 目录内容 */
    private void fetchContentsRecursive(String owner, String repo, String path,
                                         String branch, List<String[]> result) {
        String url = GITHUB_API + "/repos/" + owner + "/" + repo + "/contents/" + path;
        if (branch != null) {
            url += "?ref=" + branch;
        }

        try {
            JsonNode node = githubGet(url);
            if (node == null || !node.isArray()) return;

            for (JsonNode item : node) {
                String type = item.has("type") ? item.get("type").asText() : "";
                String itemPath = item.has("path") ? item.get("path").asText() : "";

                if ("file".equals(type)) {
                    result.add(new String[]{itemPath, item.has("download_url") ? item.get("download_url").asText() : ""});
                } else if ("dir".equals(type)) {
                    fetchContentsRecursive(owner, repo, itemPath, branch, result);
                }
            }
        } catch (Exception e) {
            log.warn("[GitHubSkill] 获取目录内容失败: path={}, error={}", path, e.getMessage());
        }
    }

    /** 获取 GitHub 文件内容（Base64 解码） */
    private String getFileContent(String owner, String repo, String path, String branch) {
        String url = GITHUB_API + "/repos/" + owner + "/" + repo + "/contents/" + path;
        if (branch != null) {
            url += "?ref=" + branch;
        }

        try {
            JsonNode node = githubGet(url);
            if (node == null || !node.has("content")) return null;

            String base64 = node.get("content").asText();
            // GitHub API 返回的 Base64 含换行符，需清理
            base64 = base64.replaceAll("\\s+", "");
            byte[] decoded = Base64.getDecoder().decode(base64);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[GitHubSkill] 获取文件内容失败: path={}", path, e);
            return null;
        }
    }

    /** 下载 ZIP 并解压到临时目录 */
    private void downloadAndExtractZip(String zipUrl, Path destDir) throws IOException {
        ResponseEntity<byte[]> resp = restTemplate.exchange(zipUrl, HttpMethod.GET, githubEntity(), byte[].class);
        byte[] zipBytes = resp.getBody();
        if (zipBytes == null || zipBytes.length == 0) {
            throw new IOException("下载 ZIP 失败: 响应为空");
        }
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                Path entryPath = destDir.resolve(entry.getName());
                // 安全检查：防止 Zip Slip
                if (!entryPath.startsWith(destDir)) {
                    throw new IOException("ZIP 条目路径不安全: " + entry.getName());
                }
                Files.createDirectories(entryPath.getParent());
                try (OutputStream os = Files.newOutputStream(entryPath)) {
                    zis.transferTo(os);
                }
            }
        }
    }

    /** 找到解压后的根目录（去掉 repo-branch/ 前缀） */
    private Path findExtractedRoot(Path tempDir) throws IOException {
        try (var stream = Files.list(tempDir)) {
            List<Path> entries = stream.toList();
            if (entries.size() == 1 && Files.isDirectory(entries.get(0))) {
                return entries.get(0);
            }
            return tempDir;
        }
    }

    /** 从 SKILL.md 路径提取 slug（父目录名） */
    private String extractSlugFromPath(String path) {
        String normalized = path.replace("\\", "/");
        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash > 0) {
            return normalized.substring(normalized.lastIndexOf('/', lastSlash - 1) + 1, lastSlash);
        }
        return normalized.replace("/" + SKILL_MD, "").replace(SKILL_MD, "");
    }
}
