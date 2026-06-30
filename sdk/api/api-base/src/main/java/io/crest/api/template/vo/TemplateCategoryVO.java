package io.crest.api.template.vo;

import lombok.Data;

@Data
// 定义页面展示或接口返回的数据结构
public class TemplateCategoryVO {
    private Integer id;

    private String name;

    private String slug;

    private Integer priority;
}
