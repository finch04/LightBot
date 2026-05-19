package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 知识库成员角色枚举
 * <p>权限等级：CREATOR > MANAGER > DEVELOPER > VIEWER</p>
 *
 * @author finch
 * @since 2026-05-19
 */
@Getter
@AllArgsConstructor
public enum KnowledgeRole {

    /** 创建者：完全权限，可删除知识库、管理所有成员 */
    CREATOR("creator", "创建者"),

    /** 管理者：可拉人、踢人、上传文档、对文档增删改查、提问 */
    MANAGER("manager", "管理者"),

    /** 开发者：可上传文档、对文档增删改查、提问 */
    DEVELOPER("developer", "开发者"),

    /** 查看者：只可提问、查看文档 */
    VIEWER("viewer", "查看者");

    @EnumValue
    private final String code;

    private final String desc;

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static KnowledgeRole fromValue(String value) {
        for (KnowledgeRole role : values()) {
            if (role.code.equalsIgnoreCase(value) || role.desc.equalsIgnoreCase(value)
                    || role.name().equalsIgnoreCase(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("未知的角色类型: " + value);
    }

    /**
     * 判断当前角色是否高于或等于目标角色
     */
    public boolean isAtLeast(KnowledgeRole target) {
        return this.ordinal() <= target.ordinal();
    }
}
