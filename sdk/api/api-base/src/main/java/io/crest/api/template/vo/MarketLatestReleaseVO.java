package io.crest.api.template.vo;

import lombok.Data;

import java.util.List;

@Data
// 定义页面展示或接口返回的数据结构
public class MarketLatestReleaseVO {

    private MarketReleaseVO release;

    private List<MarketReleaseAssetVO> assets;

}
