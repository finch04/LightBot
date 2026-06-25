package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.entity.ApiKey;
import com.lightbot.enums.ApiKeyPermission;
import com.lightbot.enums.ErrorCode;
import com.lightbot.common.BizException;
import com.lightbot.mapper.ApiKeyMapper;
import com.lightbot.service.ApiKeyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * API Key 服务实现
 *
 * @author finch
 * @since 2026-06-25
 */
@Slf4j
@Service
public class ApiKeyServiceImpl extends ServiceImpl<ApiKeyMapper, ApiKey>
        implements ApiKeyService {

    private static final String KEY_PREFIX = "lbkey_";

    @Override
    public Map<String, Object> createApiKey(Long userId, String name, String permissions, String expiresAt) {
        // 1. 生成密钥
        String rawKey = KEY_PREFIX + UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String keyHash = sha256(rawKey);
        String keyPrefix = rawKey.substring(0, 12) + "****";

        // 2. 构建实体
        ApiKey apiKey = new ApiKey();
        apiKey.setUserId(userId);
        apiKey.setName(name);
        apiKey.setKeyPrefix(keyPrefix);
        apiKey.setKeyHash(keyHash);
        apiKey.setPermissions(ApiKeyPermission.valueOf(permissions != null ? permissions.toUpperCase() : "CHAT"));
        apiKey.setIsEnabled(1);
        if (expiresAt != null && !expiresAt.isBlank()) {
            apiKey.setExpiresAt(LocalDateTime.parse(expiresAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        save(apiKey);

        // 3. 返回实体 + 完整密钥（仅此一次）
        log.info("[API Key] 创建成功 userId=[{}], name=[{}]", userId, name);
        Map<String, Object> result = new HashMap<>();
        result.put("apiKey", apiKey);
        result.put("secret", rawKey);
        return result;
    }

    @Override
    public List<ApiKey> listByUserId(Long userId) {
        return list(new LambdaQueryWrapper<ApiKey>()
                .eq(ApiKey::getUserId, userId)
                .orderByDesc(ApiKey::getCreateTime));
    }

    @Override
    public void toggleEnabled(Long id, Long userId) {
        ApiKey apiKey = getByIdAndCheckOwner(id, userId);
        apiKey.setIsEnabled(apiKey.getIsEnabled() == 1 ? 0 : 1);
        updateById(apiKey);
        log.info("[API Key] 切换状态 id=[{}], enabled=[{}]", id, apiKey.getIsEnabled());
    }

    @Override
    public void deleteApiKey(Long id, Long userId) {
        getByIdAndCheckOwner(id, userId);
        removeById(id);
        log.info("[API Key] 删除成功 id=[{}]", id);
    }

    @Override
    public Map<String, Object> regenerateApiKey(Long id, Long userId) {
        ApiKey apiKey = getByIdAndCheckOwner(id, userId);

        // 1. 生成新密钥
        String rawKey = KEY_PREFIX + UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String keyHash = sha256(rawKey);
        String keyPrefix = rawKey.substring(0, 12) + "****";

        // 2. 更新
        apiKey.setKeyHash(keyHash);
        apiKey.setKeyPrefix(keyPrefix);
        updateById(apiKey);
        log.info("[API Key] 重新生成 id=[{}]", id);

        Map<String, Object> result = new HashMap<>();
        result.put("apiKey", apiKey);
        result.put("secret", rawKey);
        return result;
    }

    @Override
    public Long authenticate(String rawKey) {
        if (rawKey == null || !rawKey.startsWith(KEY_PREFIX)) {
            return null;
        }
        String keyHash = sha256(rawKey);
        ApiKey apiKey = getOne(new LambdaQueryWrapper<ApiKey>()
                .eq(ApiKey::getKeyHash, keyHash)
                .eq(ApiKey::getIsEnabled, 1));
        if (apiKey == null) {
            return null;
        }
        // 检查过期
        if (apiKey.getExpiresAt() != null && LocalDateTime.now().isAfter(apiKey.getExpiresAt())) {
            return null;
        }
        // 异步更新最近使用时间
        baseMapper.updateLastUsedAt(apiKey.getId());
        return apiKey.getUserId();
    }

    private ApiKey getByIdAndCheckOwner(Long id, Long userId) {
        ApiKey apiKey = getById(id);
        if (apiKey == null || !apiKey.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return apiKey;
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
