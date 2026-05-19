package com.lightbot.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.LoginRequest;
import com.lightbot.dto.RegisterRequest;
import com.lightbot.dto.UserDTO;
import com.lightbot.entity.User;
import com.lightbot.enums.UserRole;
import com.lightbot.enums.UserStatus;
import com.lightbot.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 用户服务
 *
 * @author finch
 * @since 2026-05-19
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    public UserDTO register(RegisterRequest request) {
        // 1.1 用户名唯一校验
        long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (count > 0) {
            throw new BizException("用户名已存在");
        }

        // 1.2 创建用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(cn.dev33.satoken.secure.BCrypt.hashpw(request.getPassword(), cn.dev33.satoken.secure.BCrypt.gensalt()));
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        user.setEmail(request.getEmail());
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        userMapper.insert(user);

        return UserDTO.from(user);
    }

    public UserDTO login(LoginRequest request) {
        // 2.1 查找用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (user == null) {
            throw new BizException("用户名或密码错误");
        }

        // 2.2 校验密码
        if (!cn.dev33.satoken.secure.BCrypt.checkpw(request.getPassword(), user.getPassword())) {
            throw new BizException("用户名或密码错误");
        }

        // 2.3 校验状态
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BizException("账号已被禁用");
        }

        // 2.4 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        // 2.5 Sa-Token 登录
        StpUtil.login(user.getId());

        return UserDTO.from(user);
    }

    public void logout() {
        StpUtil.logout();
    }

    public UserDTO getCurrentUser() {
        long userId = StpUtil.getLoginIdAsLong();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(401, "用户不存在");
        }
        return UserDTO.from(user);
    }
}
