package io.crest.api.template.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
// 定义页面展示或接口返回的数据结构
public class MarketCategoryVO {
    private String id;
    private String name;
    private String slug;

    public MarketCategoryVO(String name) {
        this.name = name;
    }
}
