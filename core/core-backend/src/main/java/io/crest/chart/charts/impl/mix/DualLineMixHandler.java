package io.crest.chart.charts.impl.mix;

import io.crest.chart.utils.ChartDataBuild;
import io.crest.extensions.view.dto.AxisFormatResult;
import io.crest.extensions.view.dto.ChartAxis;
import io.crest.extensions.view.dto.ChartViewDTO;
import io.crest.extensions.view.dto.CustomFilterResult;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@SuppressWarnings("unchecked")
// 定义图表处理器，负责轴格式化和结果组装
public class DualLineMixHandler extends GroupMixHandler {
    @Getter
    private final String type = "chart-mix-dual-line";

    @Override
    // 根据查询结果组装图表返回数据
    public Map<String, Object> buildNormalResult(ChartViewDTO view, AxisFormatResult formatResult, CustomFilterResult filterResult, List<String[]> data) {
        boolean isDrill = filterResult
                .getFilterList()
                .stream()
                .anyMatch(ele -> ele.getFilterType() == 1);
        var xAxis = formatResult.getAxisMap().get(ChartAxis.xAxis);
        var xAxisExt = formatResult.getAxisMap().get(ChartAxis.xAxisExt);
        var yAxis = formatResult.getAxisMap().get(ChartAxis.yAxis);
        var xAxisBase = xAxis.subList(0, xAxis.size() - xAxisExt.size());
        return ChartDataBuild.transMixChartDataAntV(xAxisBase, xAxis, xAxisExt, yAxis, view, data, isDrill, true);
    }

}
