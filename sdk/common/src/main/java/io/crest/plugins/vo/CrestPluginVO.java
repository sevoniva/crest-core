package io.crest.plugins.vo;

import lombok.Data;

@Data
// 定义页面展示或接口返回的数据结构
public class CrestPluginVO {
    private String moduleName;
    private String config;
    private String icon;
    private String name;
    private String version;
    private String description;
}
