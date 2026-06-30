package io.crest.api.template.response;

import lombok.Data;

import java.util.List;

/**
 * 模板市场 V2 响应。
 */
@Data
public class MarketTemplateV2BaseResponse {

    private List<MarketTemplateV2ItemResult> items;

}
