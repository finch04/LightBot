package com.lightbot.handler;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.time.OffsetDateTime;

// 自定义序列化器：强制转为Long型秒级时间戳（无小数）
public class OffsetDateTimeToLongTimestampSerializer extends JsonSerializer<OffsetDateTime> {
    @Override
    public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value != null) {
            // 核心：转为Long类型（整数），而非Double
            long timestamp = value.toEpochSecond();
            gen.writeNumber(timestamp);
        } else {
            gen.writeNull();
        }
    }
}