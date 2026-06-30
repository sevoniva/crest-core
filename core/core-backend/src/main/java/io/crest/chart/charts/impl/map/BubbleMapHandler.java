package io.crest.chart.charts.impl.map;

import io.crest.chart.charts.impl.ExtQuotaChartHandler;
import io.crest.extensions.view.dto.AxisFormatResult;
import io.crest.extensions.view.dto.ChartViewDTO;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
@SuppressWarnings("unchecked")
// 定义图表处理器，负责轴格式化和结果组装
public class BubbleMapHandler extends ExtQuotaChartHandler {
    @Getter
    private String type = "bubble-map";

    @Override
    // 按图表配置整理维度和指标轴信息
    public AxisFormatResult formatAxis(ChartViewDTO view) {
        return super.formatAxis(view);
    }
}


