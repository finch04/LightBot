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
 * 启动时检查管理员用户状态
 * <p>检测数据库中是否存在用户，不存在时打印提示引导用户通过初始化页面创建管理员</p>
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
        long userCount = userMapper.selectCount(null);
        if (userCount == 0) {
            log.info("[AdminUser] 系统未初始化，无用户数据。请访问前端初始化页面创建管理员账号");
        }
    }
}
