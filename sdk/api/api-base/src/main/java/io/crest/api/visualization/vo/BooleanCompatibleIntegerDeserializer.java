package io.crest.api.visualization.vo;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

// 定义页面展示或接口返回的数据结构
public class BooleanCompatibleIntegerDeserializer extends JsonDeserializer<Integer> {

    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.VALUE_TRUE) {
            return 1;
        }
        if (token == JsonToken.VALUE_FALSE) {
            return 0;
        }
        if (token == JsonToken.VALUE_NUMBER_INT) {
            return parser.getIntValue();
        }
        if (token == JsonToken.VALUE_STRING) {
            String value = parser.getValueAsString().trim();
            if (value.isEmpty()) {
                return null;
            }
            if ("true".equalsIgnoreCase(value)) {
                return 1;
            }
            if ("false".equalsIgnoreCase(value)) {
                return 0;
            }
            return Integer.valueOf(value);
        }
        return (Integer) context.handleUnexpectedToken(Integer.class, parser);
    }
}
