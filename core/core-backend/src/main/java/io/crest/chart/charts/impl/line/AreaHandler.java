package io.crest.chart.charts.impl.line;

import io.crest.chart.utils.ChartDataBuild;
import io.crest.extensions.view.dto.*;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@SuppressWarnings("unchecked")
// 定义图表处理器，负责轴格式化和结果组装
public class AreaHandler extends LineHandler {
    @Getter
    private String type = "area";

    @Override
    // 按图表配置整理维度和指标轴信息
    public AxisFormatResult formatAxis(ChartViewDTO view) {
        var result = super.formatAxis(view);
        result.getAxisMap().put(ChartAxis.xAxis, view.getXAxis());
        return result;
    }


    @Override
    // 根据查询结果组装图表返回数据
    public Map<String, Object> buildNormalResult(ChartViewDTO view, AxisFormatResult formatResult, CustomFilterResult filterResult, List<String[]> data) {
        boolean isDrill = filterResult.getFilterList().stream().anyMatch(ele -> ele.getFilterType() == 1);
        var xAxis = formatResult.getAxisMap().get(ChartAxis.xAxis);
        var yAxis = formatResult.getAxisMap().get(ChartAxis.yAxis);
        return ChartDataBuild.transChartData(xAxis, yAxis, view, data, isDrill);
    }
}
