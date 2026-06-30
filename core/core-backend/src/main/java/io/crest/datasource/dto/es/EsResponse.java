package io.crest.datasource.dto.es;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
// 定义接口请求或返回数据的传输结构
public class EsResponse {
    private List<Column> columns = new ArrayList<>();
    private List<String[]> rows = new ArrayList<>();
    private String cursor;
    private Integer status;
    private Error error;
    private String version;

    @Data
    // 定义接口请求或返回数据的传输结构
    public class Error {
        private String type;
        private String reason;
    }

    @Data
    // 定义接口请求或返回数据的传输结构
    public class Column {
        private String name;
        private String type;
    }

}
