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
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(chatSessionService.listMySessions(pageNum, pageSize));
    }

    @Operation(summary = "获取会话详情")
    @GetMapping("/{id}")
    public Result<ChatSession> getById(@PathVariable Long id) {
        return Result.ok(chatSessionService.getById(id));
    }

    @Operation(summary = "获取会话的消息历史")
    @GetMapping("/{id}/messages")
    public Result<List<Message>> getMessages(@PathVariable Long id) {
        return Result.ok(messageService.listBySessionId(id));
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
}
