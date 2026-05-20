package com.lightbot.service;

import com.lightbot.dto.ChangePasswordRequest;
import com.lightbot.dto.LoginRequest;
import com.lightbot.dto.ProfileUpdateRequest;
import com.lightbot.dto.RegisterRequest;
import com.lightbot.dto.UserDTO;

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
}
