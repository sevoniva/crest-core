package io.crest.chart.charts.impl.others;

import io.crest.chart.charts.impl.DefaultChartHandler;
import io.crest.chart.charts.impl.ExtQuotaChartHandler;
import io.crest.extensions.view.dto.AxisFormatResult;
import io.crest.extensions.view.dto.ChartAxis;
import io.crest.extensions.view.dto.ChartViewDTO;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
@SuppressWarnings("unchecked")
// 定义图表处理器，负责轴格式化和结果组装
public class WordCloudHandler extends ExtQuotaChartHandler {
    @Getter
    private String type = "word-cloud";
}
