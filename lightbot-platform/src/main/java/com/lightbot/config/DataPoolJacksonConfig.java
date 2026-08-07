package com.lightbot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.lightbot.vo.DataPoolRecordMap;
import com.lightbot.vo.DataPoolRecordMapSerializer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

/**
 * 注册数据池行 Map 序列化器，确保 null 字段不被全局 NON_NULL 省略。
 *
 * @author finch
 * @since 2026-07-29
 */
@Configuration
@RequiredArgsConstructor
public class DataPoolJacksonConfig {

    private final ObjectMapper objectMapper;

    @PostConstruct
    public void registerSerializers() {
        SimpleModule module = new SimpleModule("dataPoolRecordMap");
        module.addSerializer(DataPoolRecordMap.class, new DataPoolRecordMapSerializer());
        objectMapper.registerModule(module);
    }
}
