package com.lightbot.vo;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.Map;

/**
 * 强制写出 null 字段，绕过全局 {@code NON_NULL} 对 Map 值的省略。
 *
 * @author finch
 * @since 2026-07-29
 */
public class DataPoolRecordMapSerializer extends JsonSerializer<DataPoolRecordMap> {

    @Override
    public void serialize(DataPoolRecordMap value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        gen.writeStartObject();
        if (value != null) {
            for (Map.Entry<String, Object> e : value.entrySet()) {
                gen.writeFieldName(e.getKey());
                Object v = e.getValue();
                if (v == null) {
                    gen.writeNull();
                } else {
                    serializers.defaultSerializeValue(v, gen);
                }
            }
        }
        gen.writeEndObject();
    }
}
