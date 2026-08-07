package com.lightbot.dto;

import lombok.Data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 调用方身份上下文（API Key 对话）
 * <p>用于 Tool / MCP / 问数等链路的数据与权限隔离；默认不进入系统提示词。</p>
 *
 * @author finch
 * @since 2026-08-05
 */
@Data
public class CallerContext {

    /** 上层业务终端用户标识 */
    private String externalUserId;

    /** 地区 ID */
    private String regionId;

    /** 企业 ID */
    private String enterpriseId;

    /** 完整身份扩展（姓名、角色、部门等），供 Tool/MCP 读取 */
    private Map<String, Object> profile;

    /**
     * 转为可序列化 Map（写入 ToolContext / MCP _meta / 会话 JSONB）
     *
     * @return 序列化 Map；无有效字段时返回空 Map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        if (externalUserId != null && !externalUserId.isBlank()) {
            map.put("externalUserId", externalUserId);
        }
        if (regionId != null && !regionId.isBlank()) {
            map.put("regionId", regionId);
        }
        if (enterpriseId != null && !enterpriseId.isBlank()) {
            map.put("enterpriseId", enterpriseId);
        }
        if (profile != null && !profile.isEmpty()) {
            map.put("profile", profile);
        }
        return map;
    }

    /**
     * 是否无任何隔离主键与 profile
     *
     * @return true 表示空上下文
     */
    public boolean isEmpty() {
        return (externalUserId == null || externalUserId.isBlank())
                && (regionId == null || regionId.isBlank())
                && (enterpriseId == null || enterpriseId.isBlank())
                && (profile == null || profile.isEmpty());
    }

    /**
     * 隔离主键快照（不含 profile），用于会话绑定一致性比较
     *
     * @return 仅含 externalUserId/regionId/enterpriseId 的 Map
     */
    public Map<String, String> isolationKeys() {
        Map<String, String> keys = new LinkedHashMap<>();
        if (externalUserId != null && !externalUserId.isBlank()) {
            keys.put("externalUserId", externalUserId);
        }
        if (regionId != null && !regionId.isBlank()) {
            keys.put("regionId", regionId);
        }
        if (enterpriseId != null && !enterpriseId.isBlank()) {
            keys.put("enterpriseId", enterpriseId);
        }
        return Collections.unmodifiableMap(keys);
    }

    /**
     * 从 Map 反序列化（会话 JSONB / ToolContext）
     *
     * @param map 原始 Map，可为 null
     * @return CallerContext；map 为空时返回 null
     */
    @SuppressWarnings("unchecked")
    public static CallerContext fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        CallerContext ctx = new CallerContext();
        Object eu = map.get("externalUserId");
        if (eu != null && !String.valueOf(eu).isBlank()) {
            ctx.setExternalUserId(String.valueOf(eu).trim());
        }
        Object region = map.get("regionId");
        if (region != null && !String.valueOf(region).isBlank()) {
            ctx.setRegionId(String.valueOf(region).trim());
        }
        Object enterprise = map.get("enterpriseId");
        if (enterprise != null && !String.valueOf(enterprise).isBlank()) {
            ctx.setEnterpriseId(String.valueOf(enterprise).trim());
        }
        Object profileObj = map.get("profile");
        if (profileObj instanceof Map<?, ?> profileMap && !profileMap.isEmpty()) {
            Map<String, Object> copied = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : profileMap.entrySet()) {
                if (e.getKey() != null) {
                    copied.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
            ctx.setProfile(copied);
        }
        return ctx.isEmpty() ? null : ctx;
    }
}
