package com.lightbot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightbot.common.Result;
import com.lightbot.dto.AdminUserUpdateRequest;
import com.lightbot.dto.UserDTO;
import com.lightbot.entity.Agent;
import com.lightbot.entity.Knowledge;
import com.lightbot.entity.User;
import com.lightbot.service.AgentService;
import com.lightbot.service.KnowledgeMemberService;
import com.lightbot.service.KnowledgeService;
import com.lightbot.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员专用接口（用户管理、资源查看）
 *
 * @author finch
 * @since 2026-06-18
 */
@Tag(name = "管理员管理", description = "用户管理、资源查看（仅管理员）")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final AgentService agentService;
    private final KnowledgeMemberService knowledgeMemberService;
    private final KnowledgeService knowledgeService;

    @Operation(summary = "分页查询所有用户")
    @GetMapping("/users")
    public Result<Page<User>> listUsers(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        userService.checkAdmin();
        return Result.ok(userService.listAllUsers(pageNum, pageSize, keyword));
    }

    @Operation(summary = "获取用户详情")
    @GetMapping("/users/{id}")
    public Result<UserDTO> getUser(@PathVariable Long id) {
        userService.checkAdmin();
        List<UserDTO> users = userService.getUsersByIds(List.of(id));
        if (users.isEmpty()) {
            return Result.fail("用户不存在");
        }
        return Result.ok(users.get(0));
    }

    @Operation(summary = "更新用户信息")
    @PutMapping("/users/{id}")
    public Result<Void> updateUser(@PathVariable Long id, @Valid @RequestBody AdminUserUpdateRequest request) {
        request.setUserId(id);
        userService.adminUpdateUser(request);
        return Result.ok();
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/users/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        userService.adminDeleteUser(id);
        return Result.ok();
    }

    @Operation(summary = "获取用户的Agent列表")
    @GetMapping("/users/{id}/agents")
    public Result<List<Agent>> getUserAgents(@PathVariable Long id) {
        userService.checkAdmin();
        return Result.ok(agentService.listByUserId(id));
    }

    @Operation(summary = "获取用户的知识库列表")
    @GetMapping("/users/{id}/knowledges")
    public Result<List<Knowledge>> getUserKnowledges(@PathVariable Long id) {
        userService.checkAdmin();
        List<Long> knowledgeIds = knowledgeMemberService.listKnowledgeIdsByUserId(id);
        if (knowledgeIds.isEmpty()) {
            return Result.ok(List.of());
        }
        return Result.ok(knowledgeService.listByIds(knowledgeIds));
    }
}
