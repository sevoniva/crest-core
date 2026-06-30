package io.crest.api.template.response;

import io.crest.api.template.dto.TemplateMarketDTO;
import lombok.Data;

import java.util.List;

/**
 * 模板市场兼容接口的内容节点。
 */
@Data
public class MarketTemplateInnerResult {

    private List<TemplateMarketDTO> content;

}
