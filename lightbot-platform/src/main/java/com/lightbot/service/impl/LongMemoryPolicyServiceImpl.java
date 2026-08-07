package com.lightbot.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.constant.ConfigKeys;
import com.lightbot.dto.LongMemoryKeyConfigUpdateDTO;
import com.lightbot.dto.LongMemoryPolicyUpdateDTO;
import com.lightbot.entity.ApiKey;
import com.lightbot.entity.SystemConfig;
import com.lightbot.enums.ErrorCode;
import com.lightbot.service.ApiKeyService;
import com.lightbot.service.LongMemoryPolicyService;
import com.lightbot.service.SystemConfigService;
import com.lightbot.util.JsonUtil;
import com.lightbot.vo.LongMemoryKeyConfigVO;
import com.lightbot.vo.LongMemoryPolicyVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 企业长期记忆策略服务实现
 *
 * @author finch
 * @since 2026-08-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LongMemoryPolicyServiceImpl implements LongMemoryPolicyService {

    private static final boolean DEFAULT_ENABLED = true;
    private static final boolean DEFAULT_AUTO_EXTRACT = true;
    private static final int DEFAULT_INJECT_LIMIT = 6;
    private static final String DEFAULT_SCOPE = "user";

    private final SystemConfigService systemConfigService;
    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper;

    @Override
    public LongMemoryPolicyVO getEnterprisePolicy() {
        return parseEnterprise(systemConfigService.getConfigValue(ConfigKeys.System.LONG_MEMORY_CONFIG));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LongMemoryPolicyVO updateEnterprisePolicy(LongMemoryPolicyUpdateDTO request) {
        // 1. 以现有默认为基础合并更新
        LongMemoryPolicyVO current = getEnterprisePolicy();
        if (request.getEnabled() != null) {
            current.setEnabled(request.getEnabled());
        }
        if (request.getAutoExtract() != null) {
            current.setAutoExtract(request.getAutoExtract());
        }
        if (request.getInjectLimit() != null) {
            current.setInjectLimit(clampLimit(request.getInjectLimit()));
        }
        if (request.getScope() != null) {
            current.setScope(normalizeScope(request.getScope()));
        }
        // 2. 持久化（配置项不存在时插入，兼容未跑 SQL 的环境）
        try {
            String json = objectMapper.writeValueAsString(toMap(current));
            SystemConfig existing = systemConfigService.getById(ConfigKeys.System.LONG_MEMORY_CONFIG);
            if (existing == null) {
                SystemConfig created = new SystemConfig();
                created.setConfigKey(ConfigKeys.System.LONG_MEMORY_CONFIG);
                created.setConfigValue(json);
                created.setDescription("企业长期记忆默认策略");
                systemConfigService.save(created);
            } else {
                systemConfigService.updateConfigValue(ConfigKeys.System.LONG_MEMORY_CONFIG, json);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("保存长期记忆策略失败");
        }
        log.info("[LongMemory] 企业默认策略已更新: enabled={}, autoExtract={}, injectLimit={}, scope={}",
                current.getEnabled(), current.getAutoExtract(), current.getInjectLimit(), current.getScope());
        return current;
    }

    @Override
    public LongMemoryPolicyVO resolveEffective(Long apiKeyId) {
        LongMemoryPolicyVO enterprise = getEnterprisePolicy();
        if (apiKeyId == null) {
            return enterprise;
        }
        ApiKey apiKey = apiKeyService.getById(apiKeyId);
        if (apiKey == null) {
            return enterprise;
        }
        return merge(enterprise, parseKeyOverride(apiKey.getMemoryConfig()));
    }

    @Override
    public LongMemoryKeyConfigVO getKeyConfig(Long apiKeyId) {
        ApiKey apiKey = requireApiKey(apiKeyId);
        LongMemoryKeyConfigVO vo = toKeyVo(apiKey);
        vo.setEffective(merge(getEnterprisePolicy(), parseKeyOverride(apiKey.getMemoryConfig())));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LongMemoryKeyConfigVO updateKeyConfig(Long apiKeyId, LongMemoryKeyConfigUpdateDTO request) {
        // 1. 校验 Key
        ApiKey apiKey = requireApiKey(apiKeyId);
        // 2. 跟随企业默认：清空覆盖（默认 inherit=true）
        if (request == null || !Boolean.FALSE.equals(request.getInherit())) {
            apiKey.setMemoryConfig(null);
            apiKeyService.updateById(apiKey);
            return getKeyConfig(apiKeyId);
        }
        // 3. 自定义覆盖（inherit=false）
        LongMemoryPolicyVO enterprise = getEnterprisePolicy();
        Map<String, Object> override = new LinkedHashMap<>();
        override.put("inherit", false);
        override.put("enabled", request.getEnabled() != null ? request.getEnabled() : Boolean.TRUE.equals(enterprise.getEnabled()));
        override.put("autoExtract", request.getAutoExtract() != null ? request.getAutoExtract() : Boolean.TRUE.equals(enterprise.getAutoExtract()));
        override.put("injectLimit", clampLimit(request.getInjectLimit() != null
                ? request.getInjectLimit()
                : (enterprise.getInjectLimit() != null ? enterprise.getInjectLimit() : DEFAULT_INJECT_LIMIT)));
        override.put("scope", normalizeScope(request.getScope() != null ? request.getScope() : enterprise.getScope()));
        try {
            apiKey.setMemoryConfig(objectMapper.writeValueAsString(override));
        } catch (Exception e) {
            throw new BizException("保存 API Key 记忆策略失败");
        }
        apiKeyService.updateById(apiKey);
        log.info("[LongMemory] API Key 策略已更新: apiKeyId={}, enabled={}, scope={}",
                apiKeyId, override.get("enabled"), override.get("scope"));
        return getKeyConfig(apiKeyId);
    }

    private ApiKey requireApiKey(Long apiKeyId) {
        if (apiKeyId == null) {
            throw new BizException(ErrorCode.API_KEY_NOT_FOUND);
        }
        ApiKey apiKey = apiKeyService.getById(apiKeyId);
        if (apiKey == null) {
            throw new BizException(ErrorCode.API_KEY_NOT_FOUND);
        }
        return apiKey;
    }

    private LongMemoryPolicyVO parseEnterprise(String json) {
        Map<String, Object> map = JsonUtil.parseJsonToMap(objectMapper, json);
        LongMemoryPolicyVO vo = new LongMemoryPolicyVO();
        vo.setEnabled(boolVal(map.get("enabled"), DEFAULT_ENABLED));
        vo.setAutoExtract(boolVal(map.get("autoExtract"), DEFAULT_AUTO_EXTRACT));
        vo.setInjectLimit(clampLimit(intVal(map.get("injectLimit"), DEFAULT_INJECT_LIMIT)));
        vo.setScope(normalizeScope(strVal(map.get("scope"), DEFAULT_SCOPE)));
        return vo;
    }

    private Map<String, Object> parseKeyOverride(String json) {
        if (json == null || json.isBlank()) {
            return Map.of("inherit", true);
        }
        Map<String, Object> map = JsonUtil.parseJsonToMap(objectMapper, json);
        if (map.isEmpty()) {
            return Map.of("inherit", true);
        }
        if (!map.containsKey("inherit")) {
            // 有字段即视为自定义覆盖
            map = new LinkedHashMap<>(map);
            map.put("inherit", false);
        }
        return map;
    }

    private LongMemoryPolicyVO merge(LongMemoryPolicyVO enterprise, Map<String, Object> override) {
        if (override == null || boolVal(override.get("inherit"), true)) {
            return copy(enterprise);
        }
        LongMemoryPolicyVO vo = new LongMemoryPolicyVO();
        vo.setEnabled(boolVal(override.get("enabled"), Boolean.TRUE.equals(enterprise.getEnabled())));
        vo.setAutoExtract(boolVal(override.get("autoExtract"), Boolean.TRUE.equals(enterprise.getAutoExtract())));
        vo.setInjectLimit(clampLimit(intVal(override.get("injectLimit"),
                enterprise.getInjectLimit() != null ? enterprise.getInjectLimit() : DEFAULT_INJECT_LIMIT)));
        vo.setScope(normalizeScope(strVal(override.get("scope"),
                enterprise.getScope() != null ? enterprise.getScope() : DEFAULT_SCOPE)));
        return vo;
    }

    private LongMemoryKeyConfigVO toKeyVo(ApiKey apiKey) {
        Map<String, Object> override = parseKeyOverride(apiKey.getMemoryConfig());
        LongMemoryKeyConfigVO vo = new LongMemoryKeyConfigVO();
        vo.setApiKeyId(apiKey.getId());
        boolean inherit = boolVal(override.get("inherit"), true);
        vo.setInherit(inherit);
        if (!inherit) {
            vo.setEnabled(boolVal(override.get("enabled"), true));
            vo.setAutoExtract(boolVal(override.get("autoExtract"), true));
            vo.setInjectLimit(clampLimit(intVal(override.get("injectLimit"), DEFAULT_INJECT_LIMIT)));
            vo.setScope(normalizeScope(strVal(override.get("scope"), DEFAULT_SCOPE)));
        }
        return vo;
    }

    private Map<String, Object> toMap(LongMemoryPolicyVO vo) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", Boolean.TRUE.equals(vo.getEnabled()));
        map.put("autoExtract", Boolean.TRUE.equals(vo.getAutoExtract()));
        map.put("injectLimit", clampLimit(vo.getInjectLimit() != null ? vo.getInjectLimit() : DEFAULT_INJECT_LIMIT));
        map.put("scope", normalizeScope(vo.getScope()));
        return map;
    }

    private LongMemoryPolicyVO copy(LongMemoryPolicyVO src) {
        LongMemoryPolicyVO vo = new LongMemoryPolicyVO();
        vo.setEnabled(src.getEnabled());
        vo.setAutoExtract(src.getAutoExtract());
        vo.setInjectLimit(src.getInjectLimit());
        vo.setScope(src.getScope());
        return vo;
    }

    private String normalizeScope(String scope) {
        return "agent".equalsIgnoreCase(scope) ? "agent" : DEFAULT_SCOPE;
    }

    private int clampLimit(int limit) {
        return Math.max(1, Math.min(15, limit));
    }

    private boolean boolVal(Object value, boolean defaultValue) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return defaultValue;
    }

    private int intVal(Object value, int defaultValue) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (Exception ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private String strVal(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String s = value.toString();
        return s.isBlank() ? defaultValue : s;
    }
}
