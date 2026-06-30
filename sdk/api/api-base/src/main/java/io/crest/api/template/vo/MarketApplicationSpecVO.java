package io.crest.api.template.vo;

import lombok.Data;

import java.util.List;

@Data
// 定义页面展示或接口返回的数据结构
public class MarketApplicationSpecVO {

    private String displayName;

    private String type;

    private String platformVersion;

    private String templateType;

    private String label;

    private String templateClassification;

    private String readmeName;

    // 是否推荐
    private String suggest;

    private List<MarketApplicationSpecScreenshotBaseVO> screenshots;

    private List<MarketApplicationSpecLinkVO> links;
}
