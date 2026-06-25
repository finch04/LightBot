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
          description = "读取沙盒中的文件内容。Skill 文件路径格式: skills/{slug}/filename，工作区路径格式: threads/{sessionId}/filename")
    @SystemTool(displayName = "读取沙盒文件", tags = {"file", "sandbox", "read"})
    public String readFile(
            @ToolParam(description = "文件路径，如 skills/my-skill/SKILL.md")
            @ToolParamMeta(example = "skills/my-skill/SKILL.md") String path) {
        log.info("[Tool:sandbox_read_file] path={}", path);
        if (path == null || path.isBlank()) {
            return errorJson("路径不能为空");
        }
        try {
            SandboxPath sandboxPath = parsePath(path.trim());
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
          description = "列出沙盒目录中的文件。目录路径格式: skills/{slug} 或 threads/{sessionId}")
    @SystemTool(displayName = "列出沙盒文件", tags = {"file", "sandbox", "list"})
    public String listFiles(
            @ToolParam(description = "目录路径")
            @ToolParamMeta(example = "skills/my-skill") String dirPath) {
        log.info("[Tool:sandbox_list_files] dirPath={}", dirPath);
        if (dirPath == null || dirPath.isBlank()) {
            return errorJson("目录路径不能为空");
        }
        try {
            SandboxPath sandboxPath = parsePath(dirPath.trim());
            List<String> files = sandboxFs.listFiles(sandboxPath);
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("dirPath", dirPath.trim());
            output.put("files", files);
            output.put("total", files.size());
            return toJson(output);
        } catch (Exception e) {
            return errorJson("列出文件失败: " + e.getMessage());
        }
    }

    @Tool(name = "sandbox_write_file",
          description = "写入文件到工作区（仅限 threads/ 目录）。路径格式: threads/{sessionId}/filename")
    @SystemTool(displayName = "写入沙盒文件", tags = {"file", "sandbox", "write"})
    public String writeFile(
            @ToolParam(description = "文件路径")
            @ToolParamMeta(example = "threads/123/output.txt") String path,
            @ToolParam(description = "文件内容")
            @ToolParamMeta(example = "Hello World") String content) {
        log.info("[Tool:sandbox_write_file] path={}, contentLen={}", path, content != null ? content.length() : 0);
        if (path == null || path.isBlank()) {
            return errorJson("路径不能为空");
        }
        if (content == null) {
            content = "";
        }
        try {
            SandboxPath sandboxPath = parsePath(path.trim());
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

    private SandboxPath parsePath(String fullPath) {
        String normalized = fullPath.replace("\\", "/");
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith("skills/")) {
            String relative = normalized.substring("skills/".length());
            return new SandboxPath(SandboxPath.PathType.SKILL, relative);
        } else if (normalized.startsWith("threads/")) {
            String relative = normalized.substring("threads/".length());
            return new SandboxPath(SandboxPath.PathType.WORKSPACE, relative);
        }
        throw new IllegalArgumentException("路径必须以 skills/ 或 threads/ 开头");
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
