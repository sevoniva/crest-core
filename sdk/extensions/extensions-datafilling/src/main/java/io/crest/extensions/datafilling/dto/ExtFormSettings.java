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
public class ExtFormSettings implements Serializable {

    @Serial
    private static final long serialVersionUID = -6236922011567180831L;

    private String id;
    private boolean disable;
    private List<NumberRule> numberInputRules;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    // 定义接口请求或返回数据的传输结构
    public static class NumberRule implements Serializable {
        @Serial
        private static final long serialVersionUID = -8841727448573594811L;
        private String  column;
        private String  term;

    }
}
