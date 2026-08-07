package com.lightbot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightbot.common.Result;
import cn.dev33.satoken.stp.StpUtil;
import com.lightbot.entity.ChatSession;
import com.lightbot.entity.Message;
import com.lightbot.constant.EnterpriseActors;
import com.lightbot.enums.UserRole;
import com.lightbot.service.ChatSessionService;
import com.lightbot.service.MessageService;
import com.lightbot.service.SessionFileService;
import com.lightbot.service.UserService;
import com.lightbot.vo.ConversationSearchResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 对话会话管理接口
 *
 * @author finch
 * @since 2026-05-19
 */
@Tag(name = "对话会话管理", description = "对话会话的增删改查")
@RestController
@Validated
@RequestMapping("/api/chat/sessions")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatSessionService chatSessionService;
    private final MessageService messageService;
    private final SessionFileService sessionFileService;
    private final UserService userService;

    @Operation(summary = "创建新会话")
    @PostMapping
    public Result<ChatSession> create(@RequestParam(required = false) Long agentId) {
        long userId = StpUtil.getLoginIdAsLong();
        return Result.ok(chatSessionService.createSession(userId, agentId));
    }

    @Operation(summary = "分页查询当前用户的平台调试会话列表")
    @GetMapping
    public Result<Page<ChatSession>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        long userId = StpUtil.getLoginIdAsLong();
        return Result.ok(chatSessionService.listMySessions(userId, pageNum, pageSize, keyword));
    }

    @Operation(summary = "分页查询企业 API 集成会话（建设者排障只读）")
    @GetMapping("/api-integration")
    public Result<Page<ChatSession>> listApiSessions(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.ok(chatSessionService.listApiSessions(pageNum, pageSize, keyword));
    }

    @Operation(summary = "分页查询自动化任务会话（建设者排障只读）")
    @GetMapping("/automation")
    public Result<Page<ChatSession>> listAutomationSessions(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.ok(chatSessionService.listAutomationSessions(pageNum, pageSize, keyword));
    }

    @Operation(summary = "获取会话详情")
    @GetMapping("/{id}")
    public Result<ChatSession> getById(@PathVariable Long id) {
        ensureReadable(id);
        return Result.ok(chatSessionService.getById(id));
    }

    @Operation(summary = "获取会话的消息历史（分页）")
    @GetMapping("/{id}/messages")
    public Result<Page<Message>> getMessages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        ensureReadable(id);
        return Result.ok(messageService.listBySessionIdPage(id, pageNum, pageSize));
    }

    @Operation(summary = "获取会话标题（轻量轮询，跳过缓存）")
    @GetMapping("/{id}/title")
    public Result<String> getTitle(@PathVariable Long id) {
        ensureReadable(id);
        return Result.ok(chatSessionService.getTitle(id));
    }

    @Operation(summary = "更新会话标题")
    @PutMapping("/{id}/title")
    public Result<Void> updateTitle(@PathVariable Long id,
                                    @RequestParam @NotBlank(message = "会话标题不能为空")
                                    @Size(max = 50, message = "会话标题不超过50字") String title) {
        long userId = StpUtil.getLoginIdAsLong();
        chatSessionService.ensurePlatformOwnedByUser(id, userId);
        chatSessionService.updateTitle(id, title);
        return Result.ok();
    }

    @Operation(summary = "归档会话")
    @PutMapping("/{id}/archive")
    public Result<Void> archive(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        chatSessionService.ensurePlatformOwnedByUser(id, userId);
        chatSessionService.archiveSession(id);
        return Result.ok();
    }

    @Operation(summary = "删除会话（物理删除，包含所有消息）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        ChatSession session = chatSessionService.getById(id);
        if (session != null && (EnterpriseActors.SESSION_SOURCE_API.equals(session.getSource())
                || EnterpriseActors.SESSION_SOURCE_AUTOMATION.equals(session.getSource()))) {
            // API / 自动化会话：仅管理员可清理
            userService.checkAdmin();
        } else {
            chatSessionService.ensurePlatformOwnedByUser(id, StpUtil.getLoginIdAsLong());
        }
        chatSessionService.deleteSession(id);
        return Result.ok();
    }

    @Operation(summary = "批量删除会话（物理删除，包含所有消息）")
    @DeleteMapping("/batch")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        long userId = StpUtil.getLoginIdAsLong();
        boolean admin = StpUtil.hasRole(UserRole.ADMIN.getCode());
        chatSessionService.deleteSessions(userId, ids, admin);
        return Result.ok();
    }

    @Operation(summary = "切换会话置顶状态")
    @PutMapping("/{id}/pin")
    public Result<Void> togglePin(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        chatSessionService.ensurePlatformOwnedByUser(id, userId);
        chatSessionService.togglePin(id);
        return Result.ok();
    }

    @Operation(summary = "删除单条消息（仅平台调试会话；API/自动化仅管理员）")
    @DeleteMapping("/{sessionId}/messages/{messageId}")
    public Result<Void> deleteMessage(@PathVariable Long sessionId, @PathVariable Long messageId) {
        ChatSession session = chatSessionService.getById(sessionId);
        if (session != null && (EnterpriseActors.SESSION_SOURCE_API.equals(session.getSource())
                || EnterpriseActors.SESSION_SOURCE_AUTOMATION.equals(session.getSource()))) {
            userService.checkAdmin();
        } else {
            chatSessionService.ensurePlatformOwnedByUser(sessionId, StpUtil.getLoginIdAsLong());
        }
        messageService.deleteMessage(messageId, sessionId);
        return Result.ok();
    }

    @Operation(summary = "搜索会话内的消息（模糊匹配）")
    @GetMapping("/{id}/messages/search")
    public Result<Page<Message>> searchMessages(
            @PathVariable Long id,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        ensureReadable(id);
        return Result.ok(messageService.searchBySessionId(id, keyword, pageNum, pageSize));
    }

    @Operation(summary = "跨会话搜索消息（按内容匹配）")
    @GetMapping("/search")
    public Result<List<ConversationSearchResultVO>> searchConversations(
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int limit) {
        long userId = StpUtil.getLoginIdAsLong();
        return Result.ok(messageService.searchConversations(userId, q, limit));
    }

    @Operation(summary = "按需拉取工具调用结果详情")
    @GetMapping("/tool-calls/{toolCallId}/result")
    public Result<String> getToolResultDetail(@PathVariable Long toolCallId) {
        return Result.ok(messageService.getToolResultDetail(toolCallId));
    }

    @Operation(summary = "切换消息收藏状态")
    @PutMapping("/messages/{messageId}/star")
    public Result<Void> toggleStar(@PathVariable Long messageId) {
        messageService.toggleStar(messageId);
        return Result.ok();
    }

    @Operation(summary = "获取所有收藏消息")
    @GetMapping("/messages/starred")
    public Result<Page<Message>> listStarred(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(messageService.listStarred(pageNum, pageSize));
    }

    @Operation(summary = "获取会话附件列表")
    @GetMapping("/{id}/attachments")
    public Result<List<com.lightbot.vo.SessionAttachmentVO>> getAttachments(@PathVariable Long id) {
        ensureReadable(id);
        return Result.ok(chatSessionService.getSessionAttachments(id));
    }

    @Operation(summary = "获取会话文件树（懒加载单层）")
    @GetMapping("/{id}/files/tree")
    public Result<com.lightbot.vo.SessionFileTreeResponseVO> getFileTree(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "") String path) {
        ensureReadable(id);
        return Result.ok(sessionFileService.listDirectory(id, path));
    }

    @Operation(summary = "获取会话文件内容/预览信息")
    @GetMapping("/{id}/files/content")
    public Result<com.lightbot.vo.SessionFileContentVO> getFileContent(
            @PathVariable Long id,
            @RequestParam String path) {
        ensureReadable(id);
        return Result.ok(sessionFileService.readContent(id, path));
    }

    @Operation(summary = "获取会话文件下载 URL")
    @GetMapping("/{id}/files/download")
    public Result<String> getFileDownloadUrl(
            @PathVariable Long id,
            @RequestParam String path) {
        ensureReadable(id);
        return Result.ok(sessionFileService.getDownloadUrl(id, path));
    }

    @Operation(summary = "删除会话文件")
    @DeleteMapping("/{id}/files")
    public Result<Void> deleteFile(
            @PathVariable Long id,
            @RequestParam String path) {
        long userId = StpUtil.getLoginIdAsLong();
        chatSessionService.ensurePlatformOwnedByUser(id, userId);
        sessionFileService.deleteFile(id, path);
        return Result.ok();
    }

    @Operation(summary = "移除会话附件")
    @DeleteMapping("/{id}/attachments/{attachmentId}")
    public Result<Void> removeAttachment(@PathVariable Long id, @PathVariable String attachmentId) {
        long userId = StpUtil.getLoginIdAsLong();
        chatSessionService.ensurePlatformOwnedByUser(id, userId);
        chatSessionService.removeSessionAttachment(id, attachmentId);
        return Result.ok();
    }

    @Operation(summary = "导出会话为 Markdown 或 JSON 文件")
    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportSession(
            @PathVariable Long id,
            @RequestParam(defaultValue = "markdown") String format) {
        long userId = StpUtil.getLoginIdAsLong();
        ensureReadable(id);
        String content = chatSessionService.exportSession(userId, id, format);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

        String ext = "json".equalsIgnoreCase(format) ? "json" : "md";
        String filename = "session-" + id + "." + ext;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(bytes.length)
                .body(bytes);
    }

    /**
     * 可读校验：平台会话须归属本人；API / 自动化会话任意建设者可排障只读
     */
    private void ensureReadable(Long sessionId) {
        chatSessionService.ensureReadableByUser(sessionId, StpUtil.getLoginIdAsLong());
    }
}
