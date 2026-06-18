package com.lightbot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightbot.dto.AdminUserUpdateRequest;
import com.lightbot.dto.ChangePasswordRequest;
import com.lightbot.dto.LoginRequest;
import com.lightbot.dto.ProfileUpdateRequest;
import com.lightbot.dto.RegisterRequest;
import com.lightbot.dto.UserDTO;
import com.lightbot.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 用户服务接口
 *
 * @author finch
 * @since 2026-05-19
 */
public interface UserService {

    /**
     * 用户注册
     *
     * @param request 注册请求
     * @return 用户信息
     */
    UserDTO register(RegisterRequest request);

    /**
     * 用户登录
     *
     * @param request 登录请求
     * @return 用户信息
     */
    UserDTO login(LoginRequest request);

    /**
     * 用户登出
     */
    void logout();

    /**
     * 获取当前登录用户信息
     *
     * @return 用户信息
     */
    UserDTO getCurrentUser();

    /**
     * 批量获取用户信息
     *
     * @param ids 用户ID列表
     * @return 用户信息列表
     */
    List<UserDTO> getUsersByIds(List<Long> ids);

    /**
     * 搜索用户（按用户名或昵称模糊匹配）
     *
     * @param keyword 搜索关键词
     * @return 匹配的用户列表（最多20条）
     */
    List<UserDTO> searchUsers(String keyword);

    /**
     * 更新当前用户个人信息
     *
     * @param request 更新请求
     * @return 更新后的用户信息
     */
    UserDTO updateProfile(ProfileUpdateRequest request);

    /**
     * 修改密码
     *
     * @param request 修改密码请求
     */
    void changePassword(ChangePasswordRequest request);

    /**
     * 判断是否首次登录（lastLoginAt 为 null）
     *
     * @param username 用户名
     * @return true=首次登录
     */
    boolean isFirstLogin(String username);

    /**
     * 上传当前用户头像
     *
     * @param file 头像文件
     * @return 头像URL
     */
    String uploadAvatar(MultipartFile file);

    /**
     * 校验当前用户是否为管理员，非管理员抛出权限异常
     */
    void checkAdmin();

    /**
     * 管理员分页查询所有用户
     *
     * @param pageNum  页码
     * @param pageSize 每页大小
     * @param keyword  搜索关键词（用户名/昵称）
     * @return 分页结果
     */
    Page<User> listAllUsers(int pageNum, int pageSize, String keyword);

    /**
     * 管理员更新用户信息
     *
     * @param request 更新请求
     */
    void adminUpdateUser(AdminUserUpdateRequest request);

    /**
     * 管理员删除用户（逻辑删除）
     *
     * @param userId 用户ID
     */
    void adminDeleteUser(Long userId);
}
