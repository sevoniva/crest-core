package io.crest.api.template.response;

import io.crest.api.template.dto.TemplateMarketDTO;
import lombok.Data;

import java.util.List;

/**
 * 模板市场兼容接口响应。
 */
@Data
public class MarketTemplateBaseResponse {

    private MarketTemplateInnerResult data;

}
