package com.lightbot.service;

import com.lightbot.dto.LoginRequest;
import com.lightbot.dto.RegisterRequest;
import com.lightbot.dto.UserDTO;

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
}
