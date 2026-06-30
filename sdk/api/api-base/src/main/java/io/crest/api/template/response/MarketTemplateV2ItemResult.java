package io.crest.api.template.response;

import io.crest.api.template.vo.MarketApplicationVO;
import io.crest.api.template.vo.MarketLatestReleaseVO;
import lombok.Data;

/**
 * 模板市场 V2 单项结果。
 */
@Data
public class MarketTemplateV2ItemResult {

    private MarketApplicationVO application;

    private MarketLatestReleaseVO latestRelease;

}
