package io.crest.chart.charts;

import io.crest.chart.charts.impl.DefaultChartHandler;
import io.crest.extensions.view.plugin.AbstractChartPlugin;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
// 维护图表渲染类型到处理器的运行期映射
public class ChartHandlerManager {
    @Lazy
    @Resource
    private DefaultChartHandler defaultChartHandler;
    private static final ConcurrentHashMap<String, AbstractChartPlugin> CHART_HANDLER_MAP = new ConcurrentHashMap<>();

    // 图表插件启动时注册自己的 render/type 组合
    public void registerChartHandler(String render, String type, AbstractChartPlugin chartHandler) {
        CHART_HANDLER_MAP.put(render + "-" + type, chartHandler);
    }

    // 未注册的图表类型使用默认处理器兜底
    public AbstractChartPlugin getChartHandler(String render, String type) {
        var handler =  CHART_HANDLER_MAP.get(render + "-" + type);
        if (handler == null) {
            return defaultChartHandler;
        }
        return handler;
    }
}
