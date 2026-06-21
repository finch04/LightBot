package com.lightbot.service.sandbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.SkillImportPreview;
import com.lightbot.enums.ErrorCode;
import com.lightbot.model.SkillMetadata;
import lombok.RequiredArgsConstructor;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
@RequiredArgsConstructor
public class GitHubSkillService {

    private final SkillStorageService skillStorageService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${lightbot.github.token:}")
    private String githubToken;

    private static final String GITHUB_API = "https://api.github.com";
    private static final String SKILL_MD = "SKILL.md";
    private static final String MODELSCOPE_PREFIX = "https://modelscope.cn/skills/";
    /** npx skills find 输出格式：owner/repo@skill-name [installs] */
    private static final Pattern SEARCH_LINE_PATTERN = Pattern.compile(
            "^([a-zA-Z0-9_.\\-]+/[a-zA-Z0-9_.\\-]+)@([a-zA-Z0-9_.\\-]+)(?:\\s+(.*))?$");

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
     * 全局搜索 Skill（通过 npx skills find，无需 GitHub Token）
     *
     * @param keyword 搜索关键词
     * @return Skill 摘要列表（name + source + installs）
     */
    public List<Map<String, String>> searchRemoteSkills(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        // 安全校验：防止命令注入
        if (keyword.contains("\n") || keyword.contains("\r") || keyword.contains("\0")) {
            throw new BizException(ErrorCode.BAD_REQUEST, "搜索关键字包含非法字符");
        }

        try {
            // 创建隔离临时目录（避免污染全局 npm 环境）
            Path tempHome = Files.createTempDirectory(".skills-find-");
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "npx", "-y", "skills", "find", keyword.trim()
                );
                pb.environment().put("HOME", tempHome.toString());
                pb.directory(tempHome.toFile());
                pb.redirectErrorStream(true);

                Process process = pb.start();
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                int exitCode = process.waitFor();

                if (exitCode != 0) {
                    log.warn("[GitHubSkill] npx skills find 失败: keyword={}, exitCode={}, output={}", keyword, exitCode, output);
                    return List.of();
                }

                return parseSearchOutput(output);
            } finally {
                // 清理临时目录
                Files.walk(tempHome)
                        .sorted(java.util.Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        } catch (Exception e) {
            log.warn("[GitHubSkill] 全局搜索失败: keyword={}, error={}", keyword, e.getMessage());
            return List.of();
        }
    }

    /**
     * 解析 npx skills find 输出
     * <p>格式：owner/repo@skill-name [installs]</p>
     * <p>示例：vercel-labs/agent-skills@web-design-guidelines 339.3K installs</p>
     */
    private List<Map<String, String>> parseSearchOutput(String output) {
        // 清理 ANSI 转义序列
        String cleaned = output.replaceAll("\\x1B\\[[0-?]*[ -/]*[@-~]", "")
                .replaceAll("\r", "\n");
        List<Map<String, String>> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String line : cleaned.split("\n")) {
            line = line.strip();
            // 去掉 npx 的装饰字符
            line = line.replaceAll("^[│┌└◇◒◐◓◑■●]+\\s*", "").strip();
            if (line.isEmpty()) continue;

            Matcher matcher = SEARCH_LINE_PATTERN.matcher(line);
            if (matcher.matches()) {
                String source = matcher.group(1);
                String name = matcher.group(2);
                String installs = matcher.group(3);
                String key = source + "@" + name;
                if (seen.contains(key)) continue;
                seen.add(key);

                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("name", name);
                entry.put("source", source);
                entry.put("installs", installs != null ? installs.strip() : "");
                results.add(entry);
            }
        }
        return results;
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

    // ==================== ModelScope 支持 ====================

    /**
     * 列出 ModelScope 单个 Skill 信息
     * <p>URL 格式：https://modelscope.cn/skills/{skill-id}</p>
     *
     * @param source ModelScope Skill URL
     * @return 单元素列表，包含 name 和 description
     */
    public List<Map<String, String>> listModelScopeSkills(String source) {
        String skillId = parseModelScopeSkillId(source);
        log.info("[GitHubSkill] ModelScope Skill: skillId={}", skillId);

        // 尝试通过 ModelScope API 获取 Skill 信息
        try {
            String apiUrl = "https://modelscope.cn/api/v1/skills/" + skillId;
            JsonNode node = restTemplate.getForObject(apiUrl, JsonNode.class);
            String description = "";
            String name = skillId;
            if (node != null) {
                JsonNode data = node.has("data") ? node.get("data") : node;
                if (data.has("description")) {
                    description = data.get("description").asText("");
                }
                if (data.has("name")) {
                    name = data.get("name").asText(skillId);
                }
            }
            Map<String, String> item = new LinkedHashMap<>();
            item.put("name", name);
            item.put("description", description);
            item.put("source", "modelscope");
            return List.of(item);
        } catch (Exception e) {
            log.warn("[GitHubSkill] ModelScope API 获取失败: skillId={}, error={}", skillId, e.getMessage());
            // 降级：返回基本信息
            Map<String, String> item = new LinkedHashMap<>();
            item.put("name", skillId);
            item.put("description", "ModelScope Skill");
            item.put("source", "modelscope");
            return List.of(item);
        }
    }

    /**
     * 从 ModelScope URL 解析 Skill ID
     * <p>https://modelscope.cn/skills/@org/skill-name → @org/skill-name</p>
     */
    private String parseModelScopeSkillId(String source) {
        String s = source.trim();
        if (s.startsWith(MODELSCOPE_PREFIX)) {
            s = s.substring(MODELSCOPE_PREFIX.length());
        }
        s = s.replaceAll("/+$", "");
        if (s.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "ModelScope URL 中未找到 Skill ID");
        }
        return s;
    }

    /**
     * 准备 ModelScope Skill 安装
     * <p>通过 git clone 下载 Skill 文件并暂存为草稿</p>
     *
     * @param source     ModelScope Skill URL
     * @param skillSlugs 选中的 Skill slug 列表（ModelScope 场景下只有一个）
     * @return SkillImportPreview 列表
     */
    public List<SkillImportPreview> prepareModelScopeInstall(String source, List<String> skillSlugs) {
        String skillId = parseModelScopeSkillId(source);
        String draftId = UUID.randomUUID().toString().replace("-", "");

        // ModelScope Skill 的 git 仓库地址
        String gitUrl = "https://modelscope.cn/" + skillId + ".git";
        Path tempDir = null;

        try {
            // 1. git clone 到临时目录
            tempDir = Files.createTempDirectory("modelscope-skill-");
            ProcessBuilder pb = new ProcessBuilder("git", "clone", "--depth=1", gitUrl, tempDir.resolve("skill").toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String cloneOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("[GitHubSkill] ModelScope git clone 失败: skillId={}, output={}", skillId, cloneOutput);
                throw new BizException(ErrorCode.SKILL_REMOTE_FETCH_FAILED, "ModelScope Skill 下载失败: " + skillId);
            }

            // 2. 查找 SKILL.md
            Path skillDir = tempDir.resolve("skill");
            Path skillMd = findSkillMd(skillDir);
            if (skillMd == null) {
                throw new BizException(ErrorCode.SKILL_IMPORT_FAILED, "ModelScope Skill 中未找到 SKILL.md: " + skillId);
            }

            Path skillRoot = skillMd.getParent();
            String slug = skillRoot.getFileName().toString();
            byte[] mdContent = Files.readAllBytes(skillMd);
            String skillMdContent = new String(mdContent, StandardCharsets.UTF_8);

            // 3. 暂存所有文件到草稿目录
            List<String> fileNames = new ArrayList<>();
            Files.walk(skillRoot)
                    .filter(Files::isRegularFile)
                    .forEach(file -> {
                        String relative = skillRoot.relativize(file).toString().replace("\\", "/");
                        fileNames.add(relative);
                        String destPath = "skill_drafts/" + draftId + "/" + slug + "/" + relative;
                        try (InputStream fis = Files.newInputStream(file)) {
                            byte[] fileBytes = fis.readAllBytes();
                            skillStorageService.uploadDraftFile(destPath, fileBytes);
                        } catch (Exception e) {
                            log.warn("[GitHubSkill] ModelScope 暂存文件失败: {}", destPath, e);
                        }
                    });

            // 4. 解析 SKILL.md 元数据
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

            log.info("[GitHubSkill] ModelScope 安装准备完成: skillId={}, draftId={}", skillId, draftId);
            return List.of(preview);

        } catch (BizException e) {
            skillStorageService.cleanupDraft(draftId);
            throw e;
        } catch (Exception e) {
            skillStorageService.cleanupDraft(draftId);
            throw new BizException(ErrorCode.SKILL_REMOTE_FETCH_FAILED, "ModelScope Skill 下载失败: " + e.getMessage());
        } finally {
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

    /** 在目录树中查找 SKILL.md */
    private Path findSkillMd(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            return stream
                    .filter(p -> SKILL_MD.equals(p.getFileName().toString()))
                    .findFirst()
                    .orElse(null);
        }
    }
}
