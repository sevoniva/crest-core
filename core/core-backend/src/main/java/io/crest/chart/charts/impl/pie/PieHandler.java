package io.crest.chart.charts.impl.pie;

import io.crest.chart.charts.impl.YoyChartHandler;
import io.crest.extensions.view.dto.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 饼图处理器，注册饼图系列并过滤不适合展示的负数数据
 */
@Component
@SuppressWarnings("unchecked")
public class PieHandler extends YoyChartHandler {
    /**
     * 注册饼图、玫瑰图和环图处理器
     */
    @Override
    public void init() {
        chartHandlerManager.registerChartHandler(this.getRender(), "pie", this);
        chartHandlerManager.registerChartHandler(this.getRender(), "pie-rose", this);
        chartHandlerManager.registerChartHandler(this.getRender(), "pie-donut", this);
        chartHandlerManager.registerChartHandler(this.getRender(), "pie-donut-rose", this);
    }

    /**
     * 为饼图补充扩展标签和提示字段轴
     */
    @Override
    public AxisFormatResult formatAxis(ChartViewDTO view) {
        var result = super.formatAxis(view);
        var yAxis = result.getAxisMap().get(ChartAxis.yAxis);
        yAxis.addAll(view.getExtLabel());
        yAxis.addAll(view.getExtTooltip());
        result.getAxisMap().put(ChartAxis.extLabel, view.getExtLabel());
        result.getAxisMap().put(ChartAxis.extTooltip, view.getExtTooltip());
        return result;
    }

    /**
     * 构建饼图配置并过滤负数指标数据
     */
    @Override
    public ChartViewDTO buildChart(ChartViewDTO view, ChartCalcDataResult calcResult, AxisFormatResult formatResult, CustomFilterResult filterResult) {
        ChartViewDTO result = super.buildChart(view, calcResult, formatResult, filterResult);
        filterPositiveData(result, "data", AxisChartDataAntVDTO.class);
        filterPositiveData(result, "tableRow", Map.class, view.getYAxis().get(0).getEngineFieldName());
        return result;
    }

    /**
     * 过滤正数数据根据data
     * @param result
     * @param key
     * @param clazz
     * @param <T>
     */
    private <T> void filterPositiveData(ChartViewDTO result, String key, Class<T> clazz) {
        if (result.getData().containsKey(key)) {
            List<T> list = ((List<T>) result.getData().get(key))
                    .stream()
                    .filter(item -> {
                        if (clazz == AxisChartDataAntVDTO.class) {
                            if (Objects.isNull(((AxisChartDataAntVDTO) item).getValue())) return false;
                            return ((AxisChartDataAntVDTO) item).getValue().compareTo(BigDecimal.ZERO) >= 0;
                        } else if (clazz == Map.class) {
                            return isPositive(((Map<String, Object>) item).get("value"));
                        }
                        return false;
                    })
                    .collect(Collectors.toList());
            result.getData().put(key, list);
        }
    }

    /**
     * 过滤正数数据根据tableRow
     * @param result
     * @param key
     * @param clazz
     * @param yAxisName
     * @param <T>
     */
    private <T> void filterPositiveData(ChartViewDTO result, String key, Class<T> clazz, String yAxisName) {
        if (result.getData().containsKey(key)) {
            List<T> list = ((List<T>) result.getData().get(key))
                    .stream()
                    .filter(item -> {
                        if (clazz == Map.class) {
                            Object value = ((Map<String, Object>) item).get(yAxisName);
                            return isPositive(value);
                        }
                        return false;
                    })
                    .collect(Collectors.toList());
            result.getData().put(key, list);
        }
    }

    /**
     * 判断值是否为非负数
     */
    private boolean isPositive(Object value) {
        if (value instanceof String) {
            try {
                return new BigDecimal((String) value).compareTo(BigDecimal.ZERO) >= 0;
            } catch (NumberFormatException e) {
                return false;
            }
        } else if (value instanceof BigDecimal) {
            return ((BigDecimal) value).compareTo(BigDecimal.ZERO) >= 0;
        }
        return false;
    }
}
