package com.lightbot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightbot.dto.AdminUserCreateDTO;
import com.lightbot.dto.AdminUserUpdateDTO;
import com.lightbot.dto.ChangePasswordDTO;
import com.lightbot.dto.LoginDTO;
import com.lightbot.dto.ProfileUpdateDTO;
import com.lightbot.dto.RegisterDTO;
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
     * 公开注册（已关闭，始终拒绝；请使用管理员创建用户）
     *
     * @param request 注册请求
     * @return 用户信息
     */
    UserDTO register(RegisterDTO request);

    /**
     * 管理员创建普通用户（或指定角色）账号
     *
     * @param request 创建请求
     * @return 用户信息
     */
    UserDTO adminCreateUser(AdminUserCreateDTO request);

    /**
     * 用户登录
     *
     * @param request 登录请求
     * @return 用户信息
     */
    UserDTO login(LoginDTO request);

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
    UserDTO updateProfile(ProfileUpdateDTO request);

    /**
     * 修改密码
     *
     * @param request 修改密码请求
     */
    void changePassword(ChangePasswordDTO request);

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
    void adminUpdateUser(AdminUserUpdateDTO request);

    /**
     * 管理员删除用户（逻辑删除）
     *
     * @param userId 用户ID
     */
    void adminDeleteUser(Long userId);

    /**
     * 检查系统中是否存在任何用户（用于判断是否需要初始化）
     *
     * @return true=已有用户，false=无用户（需要初始化）
     */
    boolean hasAnyUser();

    /**
     * 根据主键加载用户实体（供 Sa-Token 角色/权限数据源等仅查实体的场景使用）
     *
     * @param id 用户 ID
     * @return 用户实体，不存在返回 null
     */
    User getById(Long id);

    /**
     * 初始化管理员账号（仅系统无用户时允许调用）
     *
     * @param username 用户名
     * @param password 密码
     * @param nickname 昵称
     * @return 创建的管理员信息
     */
    UserDTO initAdmin(String username, String password, String nickname);
}
