package com.lightbot.enums;

/**
 * 枚举展示接口，统一枚举的 code 和 desc 获取方式
 *
 * @author finch
 * @since 2026-06-21
 */
public interface EnumDisplay {

    /** 获取枚举编码 */
    String getCode();

    /** 获取枚举描述 */
    String getDesc();
}
