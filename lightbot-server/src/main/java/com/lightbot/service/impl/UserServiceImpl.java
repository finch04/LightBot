package com.lightbot.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.constant.ConfigKeys;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightbot.dto.AdminUserUpdateRequest;
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
import com.lightbot.util.MinioUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户服务实现类
 *
 * @author finch
 * @since 2026-05-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Set<String> VALID_FRAMES = Set.of("lightning", "flame", "stars");
    private static final List<String> ALLOWED_AVATAR_EXTS = List.of("jpg", "jpeg", "png", "gif", "webp", "bmp");

    private final UserMapper userMapper;
    private final MinioUtil minioUtil;
    private final ObjectMapper objectMapper;

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

        // 3. 更新头像框和等级配置
        boolean configChanged = false;
        Map<String, Object> configMap = parseConfigMap(user.getConfig());

        if (request.getAvatarFrame() != null) {
            configChanged = true;
            if (request.getAvatarFrame().isEmpty() || "none".equals(request.getAvatarFrame())) {
                configMap.remove(ConfigKeys.User.AVATAR_FRAME);
            } else {
                if (!VALID_FRAMES.contains(request.getAvatarFrame())) {
                    throw new BizException(ErrorCode.BAD_REQUEST);
                }
                configMap.put(ConfigKeys.User.AVATAR_FRAME, request.getAvatarFrame());
            }
        }

        if (request.getLevel() != null) {
            configChanged = true;
            configMap.put(ConfigKeys.User.LEVEL, request.getLevel());
        }

        if (configChanged) {
            try {
                user.setConfig(objectMapper.writeValueAsString(configMap));
            } catch (Exception e) {
                throw new BizException(ErrorCode.INTERNAL_ERROR);
            }
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

    @Override
    public boolean isFirstLogin(String username) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        return user != null && user.getLastLoginAt() == null;
    }

    @Override
    public String uploadAvatar(MultipartFile file) {
        // 1. 获取当前用户
        long userId = StpUtil.getLoginIdAsLong();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        // 2. 校验文件格式
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.lastIndexOf('.') > 0) {
            ext = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
        }
        if (!ALLOWED_AVATAR_EXTS.contains(ext)) {
            throw new BizException(ErrorCode.AVATAR_UNSUPPORTED_TYPE, "支持格式: jpg/jpeg/png/gif/webp/bmp");
        }

        // 3. 生成存储路径：user/{userId}/avatar/{uuid}.{ext}
        String filePath = String.format("user/%d/avatar/%s.%s", userId, UUID.randomUUID().toString().replace("-", ""), ext);

        // 4. 删除旧头像
        deleteOldAvatar(user.getAvatar());

        // 5. 上传新头像
        minioUtil.upload(file, filePath);

        // 6. 构建永久URL并更新用户avatar字段
        String fullUrl = minioUtil.getPublicUrl(filePath);
        user.setAvatar(fullUrl);
        userMapper.updateById(user);

        log.info("[用户] 头像上传成功: userId={}, url={}", userId, fullUrl);
        return fullUrl;
    }

    private void deleteOldAvatar(String avatar) {
        if (avatar == null || avatar.isEmpty()) return;
        String path = avatar.contains("/lightbot/") ? avatar.substring(avatar.indexOf("/lightbot/") + 10) : avatar;
        try {
            minioUtil.delete(path);
        } catch (Exception e) {
            log.warn("[用户] 删除头像失败: path={}, error={}", path, e.getMessage());
        }
    }

    private Map<String, Object> parseConfigMap(String config) {
        if (config == null || config.isBlank()) return new HashMap<>();
        try {
            return objectMapper.readValue(config, new TypeReference<>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    @Override
    public void checkAdmin() {
        long userId = StpUtil.getLoginIdAsLong();
        User user = userMapper.selectById(userId);
        if (user == null || user.getRole() != UserRole.ADMIN) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    @Override
    public Page<User> listAllUsers(int pageNum, int pageSize, String keyword) {
        Page<User> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .orderByAsc(User::getCreateTime);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(User::getUsername, keyword.trim())
                    .or().like(User::getNickname, keyword.trim()));
        }
        return userMapper.selectPage(page, wrapper);
    }

    @Override
    public void adminUpdateUser(AdminUserUpdateRequest request) {
        checkAdmin();

        User user = userMapper.selectById(request.getUserId());
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        // 不允许修改自己的角色（防止误降级）
        long currentUserId = StpUtil.getLoginIdAsLong();
        if (request.getUserId().equals(currentUserId) && request.getRole() != null
                && request.getRole() != user.getRole()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "不能修改自己的角色");
        }

        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }

        userMapper.updateById(user);
        log.info("[Admin] 更新用户: userId={}, operatorId={}", request.getUserId(), currentUserId);
    }

    @Override
    public void adminDeleteUser(Long userId) {
        checkAdmin();

        // 不允许删除自己
        long currentUserId = StpUtil.getLoginIdAsLong();
        if (userId.equals(currentUserId)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "不能删除自己的账号");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }

        // 删除 MinIO 中的头像文件
        deleteOldAvatar(user.getAvatar());

        userMapper.deleteById(userId);
        log.info("[Admin] 删除用户: userId={}, operatorId={}", userId, currentUserId);
    }

    @Override
    public boolean hasAnyUser() {
        return userMapper.selectCount(null) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserDTO initAdmin(String username, String password, String nickname) {
        // 1. 系统级校验：仅无用户时允许初始化
        if (hasAnyUser()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "系统已初始化，不能重复创建管理员");
        }

        // 2. 用户名唯一校验
        long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (count > 0) {
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }

        // 3. 创建管理员账号（并发场景下由 uk_user_username 唯一索引兜底防重）
        User admin = new User();
        admin.setUsername(username);
        admin.setPassword(cn.dev33.satoken.secure.BCrypt.hashpw(password, cn.dev33.satoken.secure.BCrypt.gensalt()));
        admin.setNickname(nickname != null && !nickname.isBlank() ? nickname : username);
        admin.setEmail("");
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        try {
            userMapper.insert(admin);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }

        // 4. 自动登录
        StpUtil.login(admin.getId());

        log.info("[AdminUser] 管理员初始化成功: id={}, username={}", admin.getId(), username);
        return UserDTO.from(admin);
    }
}
