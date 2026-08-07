package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.entity.ApiKey;
import com.lightbot.enums.ApiKeyPermission;
import com.lightbot.enums.ErrorCode;
import com.lightbot.common.BizException;
import com.lightbot.mapper.ApiKeyMapper;
import com.lightbot.service.ApiKeyService;
import com.lightbot.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
    private static final String RATE_LIMIT_KEY_PREFIX = "lightbot:apikey:rate:";
    /**
     * 待 flush 到 DB 的 API Key 用量缓冲 key（Redis Hash）
     * field: apiKeyId  value: 自上次 flush 以来的累计调用次数（仅用于触发 last_used_at 刷新）
     */
    private static final String USAGE_DIRTY_KEY = "lightbot:apikey:usage:dirty";

    @Autowired
    private RedisUtil redisUtil;

    @Override
    public Map<String, Object> createApiKey(Long userId, String name, String permissions,
                                             String expiresAt, List<String> agentIds,
                                             Integer rateLimit, Integer dailyQuota) {
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
        apiKey.setPermissions(ApiKeyPermission.fromValue(permissions != null ? permissions : "chat"));
        apiKey.setAgentIds(agentIds != null && !agentIds.isEmpty() ? agentIds : null);
        apiKey.setRateLimit(rateLimit != null ? rateLimit : 60);
        apiKey.setDailyQuota(dailyQuota != null ? dailyQuota : 100000);
        apiKey.setUsedTokens(0L);
        apiKey.setIsEnabled(1);
        if (expiresAt != null && !expiresAt.isBlank()) {
            apiKey.setExpiresAt(LocalDateTime.parse(expiresAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        save(apiKey);

        // 3. 返回实体 + 完整密钥（仅此一次）
        log.info("[API Key] 创建成功 userId=[{}], name=[{}], rateLimit=[{}], dailyQuota=[{}]",
                userId, name, apiKey.getRateLimit(), apiKey.getDailyQuota());
        Map<String, Object> result = new HashMap<>();
        result.put("apiKey", apiKey);
        result.put("secret", rawKey);
        return result;
    }

    @Override
    public List<ApiKey> listAll() {
        // 企业维度：返回全部 Key（不再按创建人过滤）
        return list(new LambdaQueryWrapper<ApiKey>()
                .orderByDesc(ApiKey::getCreateTime));
    }

    @Override
    public void toggleEnabled(Long id, Long userId) {
        ApiKey apiKey = requireExists(id);
        apiKey.setIsEnabled(apiKey.getIsEnabled() == 1 ? 0 : 1);
        updateById(apiKey);
        log.info("[API Key] 切换状态 id=[{}], enabled=[{}]", id, apiKey.getIsEnabled());
    }

    @Override
    public void deleteApiKey(Long id, Long userId) {
        requireExists(id);
        removeById(id);
        log.info("[API Key] 删除成功 id=[{}]", id);
    }

    @Override
    public Map<String, Object> regenerateApiKey(Long id, Long userId) {
        ApiKey apiKey = requireExists(id);

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
        ApiKey apiKey = authenticateWithDetails(rawKey);
        // 企业服务身份：不再返回创建人 userId
        return apiKey != null ? com.lightbot.constant.EnterpriseActors.API_KEY : null;
    }

    @Override
    public ApiKey authenticateWithDetails(String rawKey) {
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
        // 仅累加 Redis 计数，定时任务批量 flush 到 DB（避免每请求一次 UPDATE 抢行锁）
        try {
            redisUtil.hashIncrement(USAGE_DIRTY_KEY, String.valueOf(apiKey.getId()), 1L);
        } catch (Exception e) {
            // Redis 异常不影响鉴权，下次 flush 会兜底
            log.warn("[API Key] 累计用量到 Redis 失败 apiKeyId={}, 放行: {}", apiKey.getId(), e.getMessage());
        }
        return apiKey;
    }

    /**
     * 定时批量 flush：把 Redis 累计的 API Key 调用次数回写到 DB 的 last_used_at
     * <p>每 5 分钟跑一次，批量 UPDATE 避开高 QPS 场景下的行锁竞争</p>
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 5 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void flushLastUsedAt() {
        Map<String, String> dirty;
        try {
            dirty = redisUtil.hashEntries(USAGE_DIRTY_KEY);
        } catch (Exception e) {
            log.warn("[API Key] 读取 Redis 用量缓冲失败，跳过本次 flush: {}", e.getMessage());
            return;
        }
        if (dirty == null || dirty.isEmpty()) {
            return;
        }
        // 先删除 dirty key（HGETALL + DEL 非原子，但 last_used_at 非关键数据，少量误差可接受）
        String[] fields = dirty.keySet().toArray(new String[0]);
        redisUtil.hashDelete(USAGE_DIRTY_KEY, fields);
        for (String idStr : fields) {
            try {
                Long id = Long.valueOf(idStr);
                baseMapper.updateLastUsedAt(id);
            } catch (NumberFormatException ex) {
                log.warn("[API Key] flush 跳过非法 id: {}", idStr);
            } catch (Exception ex) {
                log.warn("[API Key] flush last_used_at 失败 id={}: {}", idStr, ex.getMessage());
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("[API Key] flush 完成，共 {} 个 Key", fields.length);
        }
    }

    /**
     * 检查 API Key 是否有权访问指定 Agent
     *
     * @param apiKey  API Key 实体
     * @param agentId 目标 Agent ID
     * @return true=允许访问（agentIds 为空或包含目标 ID）
     */
    @Override
    public boolean checkAgentScope(ApiKey apiKey, String agentId) {
        if (agentId == null || agentId.isBlank()) return true;
        List<String> allowed = apiKey.getAgentIds();
        return allowed == null || allowed.isEmpty() || allowed.contains(agentId);
    }

    /**
     * 检查 API Key 请求频率限制（Redis 滑动窗口）
     *
     * @param apiKeyId API Key ID
     * @param rateLimit 每分钟最大请求数
     * @return true=允许，false=超限
     */
    @Override
    public boolean checkRateLimit(Long apiKeyId, int rateLimit) {
        String key = RATE_LIMIT_KEY_PREFIX + apiKeyId + ":" + (System.currentTimeMillis() / 60000);
        try {
            long count = redisUtil.increment(key);
            if (count == 1) {
                redisUtil.set(key, String.valueOf(count), 120);
            }
            return count <= rateLimit;
        } catch (Exception e) {
            // Redis 不可用时放行
            log.warn("[API Key] Redis 限流检查失败，放行: {}", e.getMessage());
            return true;
        }
    }

    @Override
    public boolean checkAndConsumeQuota(Long apiKeyId, long tokenUsage) {
        ApiKey apiKey = getById(apiKeyId);
        if (apiKey == null) return true;

        int dailyQuota = apiKey.getDailyQuota();
        if (dailyQuota <= 0) return true;

        // 1. 每日重置 + 原子扣减合并为单条 SQL，消除 TOCTOU 竞态
        //    CASE WHEN 处理跨天重置：如果 quota_reset_at 不是今天，先重置再扣减
        LocalDate today = LocalDate.now();
        int affected = baseMapper.checkAndConsumeQuota(apiKeyId, tokenUsage, dailyQuota, today);
        return affected > 0;
    }

    private ApiKey requireExists(Long id) {
        ApiKey apiKey = getById(id);
        if (apiKey == null) {
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
