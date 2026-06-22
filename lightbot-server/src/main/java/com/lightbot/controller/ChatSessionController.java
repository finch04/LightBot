package com.lightbot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightbot.common.Result;
import com.lightbot.entity.ChatSession;
import com.lightbot.entity.Message;
import com.lightbot.service.ChatSessionService;
import com.lightbot.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 对话会话管理接口
 *
 * @author finch
 * @since 2026-05-19
 */
@Tag(name = "对话会话管理", description = "对话会话的增删改查")
@RestController
@RequestMapping("/api/chat/sessions")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatSessionService chatSessionService;
    private final MessageService messageService;

    @Operation(summary = "创建新会话")
    @PostMapping
    public Result<ChatSession> create(@RequestParam(required = false) Long agentId) {
        return Result.ok(chatSessionService.createSession(agentId));
    }

    @Operation(summary = "分页查询当前用户的会话列表")
    @GetMapping
    public Result<Page<ChatSession>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.ok(chatSessionService.listMySessions(pageNum, pageSize, keyword));
    }

    @Operation(summary = "获取会话详情")
    @GetMapping("/{id}")
    public Result<ChatSession> getById(@PathVariable Long id) {
        return Result.ok(chatSessionService.getById(id));
    }

    @Operation(summary = "获取会话的消息历史（分页）")
    @GetMapping("/{id}/messages")
    public Result<Page<Message>> getMessages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(messageService.listBySessionIdPage(id, pageNum, pageSize));
    }

    @Operation(summary = "获取会话标题（轻量轮询，跳过缓存）")
    @GetMapping("/{id}/title")
    public Result<String> getTitle(@PathVariable Long id) {
        return Result.ok(chatSessionService.getTitle(id));
    }

    @Operation(summary = "更新会话标题")
    @PutMapping("/{id}/title")
    public Result<Void> updateTitle(@PathVariable Long id, @RequestParam String title) {
        chatSessionService.updateTitle(id, title);
        return Result.ok();
    }

    @Operation(summary = "归档会话")
    @PutMapping("/{id}/archive")
    public Result<Void> archive(@PathVariable Long id) {
        chatSessionService.archiveSession(id);
        return Result.ok();
    }

    @Operation(summary = "删除会话（物理删除，包含所有消息）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        chatSessionService.deleteSession(id);
        return Result.ok();
    }

    @Operation(summary = "批量删除会话（物理删除，包含所有消息）")
    @DeleteMapping("/batch")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        chatSessionService.deleteSessions(ids);
        return Result.ok();
    }

    @Operation(summary = "切换会话置顶状态")
    @PutMapping("/{id}/pin")
    public Result<Void> togglePin(@PathVariable Long id) {
        chatSessionService.togglePin(id);
        return Result.ok();
    }

    @Operation(summary = "删除单条消息")
    @DeleteMapping("/{sessionId}/messages/{messageId}")
    public Result<Void> deleteMessage(@PathVariable Long sessionId, @PathVariable Long messageId) {
        messageService.deleteMessage(messageId, sessionId);
        return Result.ok();
    }
}
