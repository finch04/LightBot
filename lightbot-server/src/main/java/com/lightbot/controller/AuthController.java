package com.lightbot.controller;

import com.lightbot.common.Result;
import com.lightbot.dto.ChangePasswordRequest;
import com.lightbot.dto.LoginRequest;
import com.lightbot.dto.ProfileUpdateRequest;
import com.lightbot.dto.RegisterRequest;
import com.lightbot.dto.UserDTO;
import com.lightbot.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "认证管理", description = "用户注册、登录、登出")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<UserDTO> register(@Valid @RequestBody RegisterRequest request) {
        UserDTO user = userService.register(request);
        return Result.ok(user);
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        UserDTO user = userService.login(request);
        Map<String, Object> data = new HashMap<>();
        data.put("token", cn.dev33.satoken.stp.StpUtil.getTokenValue());
        data.put("user", user);
        return Result.ok(data);
    }

    @Operation(summary = "用户登出")
    @PostMapping("/logout")
    public Result<Void> logout() {
        userService.logout();
        return Result.ok();
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public Result<UserDTO> me() {
        return Result.ok(userService.getCurrentUser());
    }

    @Operation(summary = "批量获取用户信息（按ID列表）")
    @GetMapping("/users/batch")
    public Result<List<UserDTO>> getUsersByIds(@RequestParam List<Long> ids) {
        return Result.ok(userService.getUsersByIds(ids));
    }

    @Operation(summary = "搜索用户（按用户名或昵称）")
    @GetMapping("/users/search")
    public Result<List<UserDTO>> searchUsers(@RequestParam String keyword) {
        return Result.ok(userService.searchUsers(keyword));
    }

    @Operation(summary = "更新个人信息")
    @PutMapping("/profile")
    public Result<UserDTO> updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        return Result.ok(userService.updateProfile(request));
    }

    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request);
        return Result.ok();
    }
}
