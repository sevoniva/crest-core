package io.crest.api.template.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.crest.api.template.vo.VisualizationTemplateExtendDataVO;
import io.crest.utils.IDUtils;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模板扩展数据传输对象，用于保存图表内置数据快照。
 */
@Data
@NoArgsConstructor
public class VisualizationTemplateExtendDataDTO extends VisualizationTemplateExtendDataVO {


    public VisualizationTemplateExtendDataDTO(Long dvId, Long viewId, String viewDetails) {
        super();
        super.setId(IDUtils.snowID());
        super.setDvId(dvId);
        super.setViewId(viewId);
        super.setViewDetails(viewDetails);
    }
}
