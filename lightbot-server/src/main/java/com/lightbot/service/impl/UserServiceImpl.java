package com.lightbot.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.ChangePasswordRequest;
import com.lightbot.dto.LoginRequest;
import com.lightbot.dto.ProfileUpdateRequest;
import com.lightbot.dto.RegisterRequest;
import com.lightbot.dto.UserDTO;
import com.lightbot.entity.User;
import com.lightbot.enums.ErrorCode;
import com.lightbot.enums.UserRole;
import com.lightbot.enums.UserStatus;
import com.lightbot.mapper.UserMapper;
import com.lightbot.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户服务实现类
 *
 * @author finch
 * @since 2026-05-19
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Override
    public UserDTO register(RegisterRequest request) {
        // 1. 用户名唯一校验
        long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (count > 0) {
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }

        // 2. 创建用户，密码BCrypt加密存储
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(cn.dev33.satoken.secure.BCrypt.hashpw(request.getPassword(), cn.dev33.satoken.secure.BCrypt.gensalt()));
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        user.setEmail(request.getEmail() != null ? request.getEmail() : "");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        userMapper.insert(user);

        return UserDTO.from(user);
    }

    @Override
    public UserDTO login(LoginRequest request) {
        // 1. 查找用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (user == null) {
            throw new BizException(ErrorCode.USERNAME_OR_PASSWORD_ERROR);
        }

        // 2. 校验密码
        if (!cn.dev33.satoken.secure.BCrypt.checkpw(request.getPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.USERNAME_OR_PASSWORD_ERROR);
        }

        // 3. 校验账号状态
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }

        // 4. 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        // 5. Sa-Token 登录，创建会话
        StpUtil.login(user.getId());

        return UserDTO.from(user);
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    @Override
    public UserDTO getCurrentUser() {
        long userId = StpUtil.getLoginIdAsLong();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        return UserDTO.from(user);
    }

    @Override
    public List<UserDTO> getUsersByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .map(UserDTO::from)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserDTO> searchUsers(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        String like = "%" + keyword.trim() + "%";
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .and(w -> w.like(User::getUsername, keyword.trim()).or().like(User::getNickname, keyword.trim()))
                        .last("LIMIT 20"));
        return users.stream().map(UserDTO::from).collect(Collectors.toList());
    }

    @Override
    public UserDTO updateProfile(ProfileUpdateRequest request) {
        // 1. 获取当前用户
        long userId = StpUtil.getLoginIdAsLong();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        // 2. 更新个人信息
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        userMapper.updateById(user);

        return UserDTO.from(user);
    }

    @Override
    public void changePassword(ChangePasswordRequest request) {
        // 1. 获取当前用户
        long userId = StpUtil.getLoginIdAsLong();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        // 2. 校验原密码
        if (!cn.dev33.satoken.secure.BCrypt.checkpw(request.getOldPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.USERNAME_OR_PASSWORD_ERROR);
        }

        // 3. 更新密码
        user.setPassword(cn.dev33.satoken.secure.BCrypt.hashpw(request.getNewPassword(), cn.dev33.satoken.secure.BCrypt.gensalt()));
        userMapper.updateById(user);
    }
}
