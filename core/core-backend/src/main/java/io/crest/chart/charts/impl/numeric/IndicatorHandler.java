package io.crest.chart.charts.impl.numeric;

import org.apache.commons.lang3.Strings;
import io.crest.chart.charts.impl.YoyChartHandler;
import io.crest.chart.utils.ChartDataBuild;
import io.crest.extensions.datasource.model.SQLMeta;
import io.crest.extensions.datasource.provider.Provider;
import io.crest.extensions.view.dto.*;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@SuppressWarnings("unchecked")
/**
 * 指标卡图表处理器
 */
public class IndicatorHandler extends YoyChartHandler {
    /**
     * 指标卡使用自定义渲染器
     */
    @Getter
    private String render = "custom";
    /**
     * 当前处理器对应的图表类型
     */
    @Getter
    private String type = "indicator";

    /**
     * 构建指标卡普通数据结果
     */
    @Override
    public Map<String, Object> buildNormalResult(ChartViewDTO view, AxisFormatResult formatResult, CustomFilterResult filterResult, List<String[]> data) {
        boolean isDrill = filterResult.getFilterList().stream().anyMatch(ele -> ele.getFilterType() == 1);
        var xAxis = formatResult.getAxisMap().get(ChartAxis.xAxis);
        var yAxis = formatResult.getAxisMap().get(ChartAxis.yAxis);
        return ChartDataBuild.transNormalChartData(xAxis, yAxis, view, data, isDrill);
    }

    /**
     * 计算指标卡数据前补齐同环比所需的 X 轴字段
     */
    @Override
    public <T extends ChartCalcDataResult> T calcChartResult(ChartViewDTO view, AxisFormatResult formatResult, CustomFilterResult filterResult, Map<String, Object> sqlMap, SQLMeta sqlMeta, Provider provider) {
        this.setIndicatorHandlerXAxis(formatResult, filterResult);
        return (T) super.calcChartResult(view, formatResult, filterResult, sqlMap, sqlMeta, provider);
    }

    /**
     * 指标卡默认只保留指标轴，维度轴按同环比配置动态补齐
     */
    @Override
    public AxisFormatResult formatAxis(ChartViewDTO view) {
        var axisMap = new HashMap<ChartAxis, List<ChartViewFieldDTO>>();
        var yAxis = new ArrayList<>(view.getYAxis());
        axisMap.put(ChartAxis.xAxis, new ArrayList<>());
        axisMap.put(ChartAxis.yAxis, yAxis);
        var context = new HashMap<String, Object>();
        return new AxisFormatResult(axisMap, context);
    }

    /**
     * 根据同环比配置从全部字段中选择指标卡维度字段
     */
    private void setIndicatorHandlerXAxis(AxisFormatResult formatResult, CustomFilterResult filterResult) {
        var xAxis = formatResult.getAxisMap().get(ChartAxis.xAxis);
        var yAxis = formatResult.getAxisMap().get(ChartAxis.yAxis);
        var allFields = (List<ChartViewFieldDTO>) filterResult.getContext().get("allFields");
        ChartViewFieldDTO yAxisChartViewFieldDTO = yAxis.get(0);
        ChartFieldCompareDTO compareCalc = yAxisChartViewFieldDTO.getCompareCalc();
        boolean isYoy = org.apache.commons.lang3.StringUtils.isNotEmpty(compareCalc.getType())
                && !org.apache.commons.lang3.Strings.CI.equals(compareCalc.getType(), "none");
        if (isYoy) {
            xAxis.clear();
            // 设置维度字段，从同环比中获取用户选择的字段
            xAxis.addAll(allFields.stream().filter(i -> org.springframework.util.StringUtils.endsWithIgnoreCase(i.getId().toString(), compareCalc.getField().toString())).toList());
            xAxis.get(0).setSort("desc");
            if(Objects.isNull(compareCalc.getCustom())){
                xAxis.get(0).setDateStyle("y_M_d");
            }else{
                xAxis.get(0).setDateStyle(compareCalc.getCustom().getTimeType());
            }
        }
        formatResult.getAxisMap().put(ChartAxis.xAxis, xAxis);
    }
}
