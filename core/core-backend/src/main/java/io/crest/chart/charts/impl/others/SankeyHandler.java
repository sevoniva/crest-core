package io.crest.chart.charts.impl.others;

import io.crest.chart.charts.impl.GroupChartHandler;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
@SuppressWarnings("unchecked")
// 定义图表处理器，负责轴格式化和结果组装
public class SankeyHandler extends GroupChartHandler {
    @Getter
    private String type = "sankey";
}
