package io.crest.chart.charts.impl.map;

import io.crest.chart.charts.impl.DefaultChartHandler;
import io.crest.chart.utils.ChartDataBuild;
import io.crest.extensions.view.dto.*;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@SuppressWarnings("unchecked")
// 定义图表处理器，负责轴格式化和结果组装
public class HeatMapHandler extends DefaultChartHandler {
    @Getter
    private String type = "heat-map";

    @Override
    // 根据查询结果组装图表返回数据
    public Map<String, Object> buildResult(ChartViewDTO view, AxisFormatResult formatResult, CustomFilterResult filterResult, List<String[]> data) {
        boolean isDrill = filterResult
                .getFilterList()
                .stream()
                .anyMatch(ele -> ele.getFilterType() == 1);
        var xAxis = formatResult.getAxisMap().get(ChartAxis.xAxis);
        var yAxis = formatResult.getAxisMap().get(ChartAxis.yAxis);
        Map<String, Object> result = ChartDataBuild.transHeatMapChartDataAntV(xAxis, xAxis, yAxis, view, data, isDrill);
        return result;
    }
}
