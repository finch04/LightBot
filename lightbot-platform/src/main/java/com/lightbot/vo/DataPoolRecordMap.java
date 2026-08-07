package com.lightbot.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据池行数据 Map。
 * <p>
 * 全局 Jackson 为 {@code NON_NULL} 时会省略普通 Map 中的 null 值；
 * 通过专用序列化器强制保留空字段，保证返回完整字段结构。
 *
 * @author finch
 * @since 2026-07-29
 */
@JsonSerialize(using = DataPoolRecordMapSerializer.class)
public class DataPoolRecordMap extends LinkedHashMap<String, Object> {

    public DataPoolRecordMap() {
        super();
    }

    public DataPoolRecordMap(int initialCapacity) {
        super(initialCapacity);
    }

    public DataPoolRecordMap(Map<? extends String, ?> m) {
        super(m);
    }
}
