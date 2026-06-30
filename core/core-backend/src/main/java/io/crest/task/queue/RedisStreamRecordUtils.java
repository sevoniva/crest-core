package io.crest.task.queue;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class RedisStreamRecordUtils {

    private RedisStreamRecordUtils() {
    }

    public static String stringField(Map<?, ?> value, String fieldName) {
        return stringValue(field(value, fieldName));
    }

    public static Long longField(Map<?, ?> value, String fieldName) {
        String text = stringField(value, fieldName);
        if (StringUtils.isBlank(text)) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Object field(Map<?, ?> value, String fieldName) {
        if (value == null || StringUtils.isBlank(fieldName)) {
            return null;
        }
        Object directValue = value.get(fieldName);
        if (directValue != null || value.containsKey(fieldName)) {
            return directValue;
        }
        for (Map.Entry<?, ?> entry : value.entrySet()) {
            if (fieldName.equals(stringValue(entry.getKey()))) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return value.toString();
    }
}
