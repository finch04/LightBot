package com.lightbot.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.entity.ApiKey;

import java.util.List;
import java.util.Map;

/**
 * API Key 服务接口
 *
 * @author finch
 * @since 2026-06-25
 */
public interface ApiKeyService extends IService<ApiKey> {

    /**
     * 创建 API Key
     *
     * @param userId      所属用户ID
     * @param name        Key名称
     * @param permissions 权限范围
     * @param expiresAt   过期时间（null=永不过期）
     * @param agentIds    绑定的Agent ID列表（null=全部）
     * @param rateLimit   每分钟调用上限（null=默认60）
     * @param dailyQuota  每日Token配额（null=默认100000）
     * @return {apiKey: ApiKey实体（不含hash）, secret: 完整密钥（仅此一次返回）}
     */
    Map<String, Object> createApiKey(Long userId, String name, String permissions,
                                     String expiresAt, List<String> agentIds,
                                     Integer rateLimit, Integer dailyQuota);

    /**
     * 查询用户的 API Key 列表
     */
    List<ApiKey> listByUserId(Long userId);

    /**
     * 启用/禁用 API Key
     */
    void toggleEnabled(Long id, Long userId);

    /**
     * 删除 API Key
     */
    void deleteApiKey(Long id, Long userId);

    /**
     * 重新生成 API Key（吊销旧密钥，生成新密钥）
     *
     * @return {apiKey: ApiKey实体, secret: 新的完整密钥}
     */
    Map<String, Object> regenerateApiKey(Long id, Long userId);

    /**
     * 验证 API Key 并返回关联的用户ID（用于认证拦截器）
     *
     * @param rawKey 完整密钥（lbkey_xxx）
     * @return 用户ID，验证失败返回null
     */
    Long authenticate(String rawKey);

    /**
     * 验证 API Key 并返回完整实体（含限流/作用域信息）
     *
     * @param rawKey 完整密钥（lbkey_xxx）
     * @return ApiKey 实体，验证失败返回null
     */
    ApiKey authenticateWithDetails(String rawKey);

    /**
     * 检查并扣减每日 Token 配额
     *
     * @param apiKeyId   API Key ID
     * @param tokenUsage 本次消耗的 Token 数
     * @return true=配额充足，false=超配额
     */
    boolean checkAndConsumeQuota(Long apiKeyId, long tokenUsage);
}
