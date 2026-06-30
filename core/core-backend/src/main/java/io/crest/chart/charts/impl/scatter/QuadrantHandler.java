package io.crest.chart.charts.impl.scatter;

import io.crest.chart.charts.impl.YoyChartHandler;
import io.crest.chart.utils.ChartDataBuild;
import io.crest.extensions.view.dto.*;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@SuppressWarnings("unchecked")
// 定义图表处理器，负责轴格式化和结果组装
public class QuadrantHandler extends YoyChartHandler {
    @Getter
    private String type = "quadrant";

    @Override
    // 按图表配置整理维度和指标轴信息
    public AxisFormatResult formatAxis(ChartViewDTO view) {
        var result = super.formatAxis(view);
        var yAxis = new ArrayList<>(view.getYAxis());
        yAxis.addAll(view.getYAxisExt());
        yAxis.addAll(view.getExtBubble());
        yAxis.addAll(view.getExtTooltip());
        result.getAxisMap().put(ChartAxis.yAxis, yAxis);
        result.getAxisMap().put(ChartAxis.extBubble, view.getExtBubble());
        result.getAxisMap().put(ChartAxis.extTooltip, view.getExtTooltip());
        result.getAxisMap().put(ChartAxis.yAxisExt, view.getYAxisExt());
        return result;
    }

    @Override
    // 根据查询结果组装图表返回数据
    public Map<String, Object> buildNormalResult(ChartViewDTO view, AxisFormatResult formatResult, CustomFilterResult filterResult, List<String[]> data) {
        boolean isDrill = filterResult.getFilterList().stream().anyMatch(ele -> ele.getFilterType() == 1);
        var xAxis = formatResult.getAxisMap().get(ChartAxis.xAxis);
        var yAxis = formatResult.getAxisMap().get(ChartAxis.yAxis);
        Map<String, Object> result = ChartDataBuild.transMixChartDataAntV(xAxis, xAxis, new ArrayList<>(), yAxis, view, data, isDrill);
        return result;
    }
}
