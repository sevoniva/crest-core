package io.crest.extensions.datafilling.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
// 定义接口请求或返回数据的传输结构
public class ExtIndexField implements Serializable {
    @Serial
    private static final long serialVersionUID = -3169849285437114316L;

    private String name;

    private List<ColumnSetting> columns;

    private boolean removed;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    // 定义接口请求或返回数据的传输结构
    public static class ColumnSetting {
        private String column;
        private String order;
    }
}
