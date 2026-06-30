package io.crest.chart.charts.impl.others;

import io.crest.chart.charts.impl.ExtQuotaChartHandler;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
@SuppressWarnings("unchecked")
// 定义图表处理器，负责轴格式化和结果组装
public class TreemapHandler extends ExtQuotaChartHandler {
    @Getter
    private String type = "treemap";
}
