package com.lightbot.dto.askdata;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 兼容旧版 filters：{@code {"status":"active"}} → {@code [{field, op:eq, value}]}
 *
 * @author finch
 * @since 2026-07-31
 */
public class AskFilterListDeserializer extends JsonDeserializer<List<AskFilterDef>> {

    @Override
    public List<AskFilterDef> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        JsonNode node = mapper.readTree(p);
        if (node == null || node.isNull()) {
            return new ArrayList<>();
        }
        if (node.isArray()) {
            List<AskFilterDef> list = mapper.convertValue(node, new TypeReference<>() {});
            return list != null ? list : new ArrayList<>();
        }
        if (node.isObject()) {
            List<AskFilterDef> list = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                AskFilterDef f = new AskFilterDef();
                f.setField(e.getKey());
                f.setOp("eq");
                f.setValue(mapper.convertValue(e.getValue(), Object.class));
                list.add(f);
            }
            return list;
        }
        return new ArrayList<>();
    }
}
