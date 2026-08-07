package com.lightbot.util;

import com.lightbot.common.BizException;
import com.lightbot.dto.CallerContext;
import com.lightbot.enums.ErrorCode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 调用方身份上下文规范化与绑定校验
 *
 * @author finch
 * @since 2026-08-05
 */
public final class CallerContextUtil {

    public static final int MAX_ID_LENGTH = 128;

    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9._\\-:@/]{1,128}$");

    private CallerContextUtil() {
    }

    /**
     * 合并顶层 externalUserId 到 callerContext，并规范化各隔离主键
     *
     * @param callerContext   请求中的 callerContext（可为 null）
     * @param externalUserId  顶层 externalUserId（兼容字段）
     * @return 规范化后的上下文；全空则 null
     */
    public static CallerContext mergeAndNormalize(CallerContext callerContext, String externalUserId) {
        CallerContext merged = callerContext != null ? copy(callerContext) : new CallerContext();
        String topLevel = ExternalUserIdUtil.normalize(externalUserId);
        if (merged.getExternalUserId() == null || merged.getExternalUserId().isBlank()) {
            merged.setExternalUserId(topLevel);
        } else {
            merged.setExternalUserId(ExternalUserIdUtil.normalize(merged.getExternalUserId()));
            if (topLevel != null && !Objects.equals(topLevel, merged.getExternalUserId())) {
                throw new BizException(ErrorCode.API_CALLER_CONTEXT_INVALID,
                        "顶层 externalUserId 与 callerContext.externalUserId 不一致");
            }
        }
        merged.setRegionId(normalizeId(merged.getRegionId(), "regionId"));
        merged.setEnterpriseId(normalizeId(merged.getEnterpriseId(), "enterpriseId"));
        if (merged.getProfile() != null && merged.getProfile().isEmpty()) {
            merged.setProfile(null);
        }
        return merged.isEmpty() ? null : merged;
    }

    /**
     * 规范化单个隔离 ID（空白→null；非法→异常）
     *
     * @param raw   原始值
     * @param field 字段名（用于错误信息）
     * @return 规范化值或 null
     */
    public static String normalizeId(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.length() > MAX_ID_LENGTH || !ID_PATTERN.matcher(value).matches()) {
            throw new BizException(ErrorCode.API_CALLER_CONTEXT_INVALID,
                    field + " 无效：最长128字符，仅允许字母、数字与 . _ - : @ /");
        }
        return value;
    }

    /**
     * 校验请求隔离主键与会话已绑定值一致；会话未绑定的键允许首次写入
     *
     * @param bound   会话已绑定上下文（可为 null）
     * @param request 本轮请求上下文（可为 null）
     */
    public static void assertIsolationCompatible(CallerContext bound, CallerContext request) {
        if (bound == null || bound.isEmpty() || request == null || request.isEmpty()) {
            return;
        }
        assertKeyCompatible(bound.getExternalUserId(), request.getExternalUserId());
        assertKeyCompatible(bound.getRegionId(), request.getRegionId());
        assertKeyCompatible(bound.getEnterpriseId(), request.getEnterpriseId());
    }

    /**
     * 合并会话绑定与请求：已绑定主键优先保留；请求补全新键；profile 请求优先否则沿用会话
     *
     * @param bound   会话已绑定
     * @param request 本轮请求
     * @return 合并后的有效上下文
     */
    public static CallerContext resolveEffective(CallerContext bound, CallerContext request) {
        if (bound == null || bound.isEmpty()) {
            return request == null || request.isEmpty() ? null : copy(request);
        }
        if (request == null || request.isEmpty()) {
            return copy(bound);
        }
        CallerContext effective = copy(bound);
        if (effective.getExternalUserId() == null) {
            effective.setExternalUserId(request.getExternalUserId());
        }
        if (effective.getRegionId() == null) {
            effective.setRegionId(request.getRegionId());
        }
        if (effective.getEnterpriseId() == null) {
            effective.setEnterpriseId(request.getEnterpriseId());
        }
        if (request.getProfile() != null && !request.getProfile().isEmpty()) {
            effective.setProfile(new LinkedHashMap<>(request.getProfile()));
        }
        return effective.isEmpty() ? null : effective;
    }

    /**
     * 转为会话 JSONB 友好 Map
     *
     * @param ctx 上下文
     * @return Map；null 时返回 null
     */
    public static Map<String, Object> toPersistMap(CallerContext ctx) {
        return ctx == null || ctx.isEmpty() ? null : ctx.toMap();
    }

    private static void assertKeyCompatible(String bound, String request) {
        if (bound != null && !bound.isBlank() && request != null && !request.isBlank()
                && !Objects.equals(bound, request)) {
            throw new BizException(ErrorCode.API_CALLER_CONTEXT_MISMATCH);
        }
    }

    private static CallerContext copy(CallerContext src) {
        CallerContext copy = new CallerContext();
        copy.setExternalUserId(src.getExternalUserId());
        copy.setRegionId(src.getRegionId());
        copy.setEnterpriseId(src.getEnterpriseId());
        if (src.getProfile() != null) {
            copy.setProfile(new LinkedHashMap<>(src.getProfile()));
        }
        return copy;
    }
}
