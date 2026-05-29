package com.lightbot.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lightbot.entity.User;
import com.lightbot.enums.UserRole;
import com.lightbot.enums.UserStatus;
import com.lightbot.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时自动创建管理员用户
 * <p>检测数据库中是否存在 admin 角色用户，不存在则自动创建默认管理员（admin/admin123）</p>
 *
 * @author finch
 * @since 2026-05-29
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class AdminUserInitializer implements ApplicationRunner {

    private final UserMapper userMapper;

    @Override
    public void run(ApplicationArguments args) {
        // 1. 已有 ADMIN 角色用户则跳过
        long adminCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getRole, UserRole.ADMIN));
        if (adminCount > 0) {
            return;
        }
        // 2. 用户名 admin 已占用则跳过，避免 uk_user_username 冲突导致应用启动失败
        long adminUsernameCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, "admin"));
        if (adminUsernameCount > 0) {
            log.warn("[AdminUser] 用户名 admin 已存在但无 ADMIN 角色用户，跳过默认管理员创建");
            return;
        }
        // 3. 创建默认管理员
        User admin = new User();
        admin.setUsername("admin");
        admin.setPassword(cn.dev33.satoken.secure.BCrypt.hashpw("admin123", cn.dev33.satoken.secure.BCrypt.gensalt()));
        admin.setNickname("管理员");
        admin.setEmail("");
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        userMapper.insert(admin);
        log.info("[AdminUser] 创建默认管理员: id={}, username=admin", admin.getId());
    }
}
