package com.lightbot.tool.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.service.sandbox.SandboxFs;
import com.lightbot.service.sandbox.SandboxPath;
import com.lightbot.tool.annotation.SystemTool;
import com.lightbot.tool.annotation.ToolParamMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置工具 — 沙盒文件操作
 * <p>提供 Skill 只读访问和工作区读写能力。</p>
 * <p>工作区路径自动注入当前会话 ID，AI 只需传相对路径。</p>
 *
 * @author finch
 * @since 2026-06-24
 */
@Slf4j
@Component("sandboxFileTool")
@RequiredArgsConstructor
@SystemTool(displayName = "沙盒文件操作", description = "在沙盒中读写文件，支持 Skill 只读访问和工作区读写",
        tags = {"file", "sandbox"})
public class SandboxFileTool {

    private final SandboxFs sandboxFs;
    private final ObjectMapper objectMapper;

    @Tool(name = "sandbox_read_file",
          description = "读取沙盒中的文件内容。两种路径模式：" +
                  "1) Skill 文件（只读）: skills/{skillSlug}/filename，如 skills/my-skill/SKILL.md。" +
                  "2) 工作区文件（读写）: 直接传相对路径如 output.txt 或 data/result.json，系统自动归属到当前会话工作区。")
    @SystemTool(displayName = "读取沙盒文件", tags = {"file", "sandbox", "read"})
    public String readFile(
            @ToolParam(description = "文件路径。Skill 文件以 skills/ 开头（如 skills/my-skill/SKILL.md）；工作区文件传相对路径（如 output.txt）")
            @ToolParamMeta(example = "skills/my-skill/SKILL.md") String path,
            ToolContext toolContext) {
        log.info("[Tool:sandbox_read_file] path={}", path);
        if (path == null || path.isBlank()) {
            return errorJson("路径不能为空");
        }
        try {
            SandboxPath sandboxPath = resolvePath(path.trim(), toolContext);
            String content = sandboxFs.readFile(sandboxPath);
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("path", path.trim());
            output.put("content", content);
            output.put("size", content.length());
            return toJson(output);
        } catch (Exception e) {
            return errorJson("读取文件失败: " + e.getMessage());
        }
    }

    @Tool(name = "sandbox_list_files",
          description = "列出沙盒目录中的文件。两种路径模式：" +
                  "1) Skill 目录（只读）: skills/{skillSlug}，如 skills/my-skill。" +
                  "2) 工作区目录（读写）: 直接传相对路径如 output 或 data，系统自动归属到当前会话工作区。" +
                  "不传 path 则列出当前会话工作区根目录。")
    @SystemTool(displayName = "列出沙盒文件", tags = {"file", "sandbox", "list"})
    public String listFiles(
            @ToolParam(description = "目录路径。Skill 目录以 skills/ 开头（如 skills/my-skill）；工作区目录传相对路径（如 data）。不传则列出工作区根目录")
            @ToolParamMeta(example = "skills/my-skill") String dirPath,
            ToolContext toolContext) {
        log.info("[Tool:sandbox_list_files] dirPath={}", dirPath);
        String path = (dirPath == null || dirPath.isBlank()) ? "" : dirPath.trim();
        try {
            SandboxPath sandboxPath = resolvePath(path, toolContext);
            List<String> files = sandboxFs.listFiles(sandboxPath);
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("dirPath", path.isEmpty() ? "(工作区根目录)" : path);
            output.put("files", files);
            output.put("total", files.size());
            return toJson(output);
        } catch (Exception e) {
            return errorJson("列出文件失败: " + e.getMessage());
        }
    }

    @Tool(name = "sandbox_write_file",
          description = "写入文件到当前会话。两种写入区域：" +
                  "1) 工作区（默认，临时/中间文件）: 直接传相对路径如 output.txt 或 data/result.json。" +
                  "2) AI 产出区（用于交付给用户的最终文件）: 路径以 outputs/ 开头，如 outputs/files/report.pdf，" +
                  "可配合 present_artifacts 工具交付。" +
                  "禁止写入 skills/ 路径（只读）。如需创建子目录，直接在路径中包含即可。")
    @SystemTool(displayName = "写入沙盒文件", tags = {"file", "sandbox", "write"})
    public String writeFile(
            @ToolParam(description = "相对路径。工作区文件如 output.txt；交付文件以 outputs/ 开头如 outputs/files/report.pdf。不要传 skills/ 开头的路径")
            @ToolParamMeta(example = "output.txt") String path,
            @ToolParam(description = "文件内容")
            @ToolParamMeta(example = "Hello World") String content,
            ToolContext toolContext) {
        log.info("[Tool:sandbox_write_file] path={}, contentLen={}", path, content != null ? content.length() : 0);
        if (path == null || path.isBlank()) {
            return errorJson("路径不能为空");
        }
        if (content == null) {
            content = "";
        }
        try {
            SandboxPath sandboxPath = resolvePath(path.trim(), toolContext);
            sandboxFs.writeFile(sandboxPath, content);
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("path", path.trim());
            output.put("size", content.length());
            output.put("success", true);
            return toJson(output);
        } catch (Exception e) {
            return errorJson("写入文件失败: " + e.getMessage());
        }
    }

    /**
     * 解析路径：skills/ 开头走 Skill 路径，outputs/ 开头走 AI 产出区，其余自动归属到当前会话工作区
     */
    private SandboxPath resolvePath(String path, ToolContext toolContext) {
        String normalized = path.replace("\\", "/");
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        // Skill 路径：skills/{slug}/...（只读）
        if (normalized.startsWith("skills/")) {
            String relative = normalized.substring("skills/".length());
            if (relative.isBlank()) {
                throw new IllegalArgumentException("Skill 路径不完整，需指定文件或目录");
            }
            return new SandboxPath(SandboxPath.PathType.SKILL, relative);
        }
        String sessionId = extractSessionId(toolContext);
        // AI 产出区：outputs/...（读写，用于交付物）
        if (normalized.startsWith("outputs/")) {
            String relative = normalized.substring("outputs/".length());
            return SandboxPath.output(sessionId, relative);
        }
        // 工作区路径：自动注入 sessionId
        return SandboxPath.workspace(sessionId, normalized);
    }

    /**
     * 从 ToolContext 提取 sessionId
     */
    private String extractSessionId(ToolContext toolContext) {
        if (toolContext != null) {
            Object sid = toolContext.getContext().get("sessionId");
            if (sid != null) {
                return String.valueOf(sid);
            }
        }
        return "default";
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"error\":\"序列化失败\"}";
        }
    }

    private String errorJson(String message) {
        return "{\"success\":false,\"error\":\"" + message.replace("\"", "\\\"") + "\"}";
    }
}
