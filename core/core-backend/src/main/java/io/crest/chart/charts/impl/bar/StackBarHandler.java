package io.crest.chart.charts.impl.bar;

import io.crest.chart.utils.ChartDataBuild;
import io.crest.extensions.view.dto.*;
import lombok.Getter;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@SuppressWarnings("unchecked")
/**
 * 堆叠柱状图处理器
 */
public class StackBarHandler extends BarHandler {
    /**
     * 当前处理器对应的主图表类型
     */
    @Getter
    private String type = "bar-stack";
    /**
     * 注册堆叠柱状图及其横向、百分比变体
     */
    @Override
    public void init() {
        chartHandlerManager.registerChartHandler(this.getRender(), "bar-stack", this);
        chartHandlerManager.registerChartHandler(this.getRender(), "bar-stack-horizontal", this);
        chartHandlerManager.registerChartHandler(this.getRender(), "percentage-bar-stack", this);
        chartHandlerManager.registerChartHandler(this.getRender(), "percentage-bar-stack-horizontal", this);
    }
    /**
     * 将堆叠维度合并到 X 轴格式化结果
     */
    @Override
    public AxisFormatResult formatAxis(ChartViewDTO view) {
        var result = super.formatAxis(view);
        var xAxis = result.getAxisMap().get(ChartAxis.xAxis);
        xAxis.addAll(view.getExtStack());
        result.getAxisMap().put(ChartAxis.extStack, view.getExtStack());
        return result;
    }
    /**
     * 处理堆叠维度下钻时的过滤条件和轴字段
     */
    @Override
    public <T extends CustomFilterResult> T customFilter(ChartViewDTO view, List<ChartExtFilterDTO> filterList, AxisFormatResult formatResult) {
        var result = super.customFilter(view, filterList, formatResult);
        List<ChartDrillRequest> drillRequestList = view.getChartExtRequest().getDrill();
        var drillFields = formatResult.getAxisMap().get(ChartAxis.drill);
        // 堆叠维度下钻
        if (ObjectUtils.isNotEmpty(drillRequestList) && (drillFields.size() > drillRequestList.size())) {
            List<ChartExtFilterDTO> noDrillFilterList = filterList
                    .stream()
                    .filter(ele -> ele.getFilterType() != 1)
                    .collect(Collectors.toList());
            var noDrillFieldAxis = formatResult.getAxisMap().get(ChartAxis.xAxis)
                    .stream()
                    .filter(ele -> ele.getSource() != FieldSource.DRILL)
                    .collect(Collectors.toList());
            List<ChartExtFilterDTO> drillFilters = new ArrayList<>();
            ArrayList<ChartViewFieldDTO> fieldsToFilter = new ArrayList<>();
            var extStack = formatResult.getAxisMap().get(ChartAxis.extStack);
            if (ObjectUtils.isNotEmpty(extStack) &&
                    Objects.equals(drillFields.get(0).getId(), extStack.get(0).getId())) {
                fieldsToFilter.addAll(view.getXAxis());
                groupStackDrill(noDrillFieldAxis, noDrillFilterList, fieldsToFilter, drillFields, drillRequestList);
                formatResult.getAxisMap().put(ChartAxis.xAxis, noDrillFieldAxis);
                result.setFilterList(noDrillFilterList);
            }
        }
        return (T) result;
    }
    /**
     * 构建堆叠柱状图前端数据结构
     */
    @Override
    public Map<String, Object> buildNormalResult(ChartViewDTO view, AxisFormatResult formatResult, CustomFilterResult filterResult, List<String[]> data) {
        boolean isDrill = filterResult.getFilterList().stream().anyMatch(ele -> ele.getFilterType() == 1);
        var xAxis = formatResult.getAxisMap().get(ChartAxis.xAxis);
        var extStack = formatResult.getAxisMap().get(ChartAxis.extStack);
        var yAxis = formatResult.getAxisMap().get(ChartAxis.yAxis);
        var drillAxis = xAxis.stream().filter(axis -> FieldSource.DRILL == axis.getSource()).toList();
        var xAxisBase = xAxis.subList(0, xAxis.size() - extStack.size() - drillAxis.size());
        return ChartDataBuild.transStackChartDataAntV(xAxisBase, xAxis, yAxis, view, data, extStack, isDrill);
    }
}
