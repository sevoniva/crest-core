package io.crest.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// 提供当前模块复用的工具能力
public class LongArray2StringSerialize extends JsonSerializer<List<Long>> {

    @Override
    public void serialize(List<Long> longs, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        List<String> list = new ArrayList<>();
        for (Long str : longs) {
            if (str != null) {
                list.add(str.toString());
            }
        }
        jsonGenerator.writeObject(list);
    }
}
