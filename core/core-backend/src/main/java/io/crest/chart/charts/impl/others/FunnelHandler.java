package io.crest.chart.charts.impl.others;

import io.crest.chart.charts.impl.ExtQuotaChartHandler;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
@SuppressWarnings("unchecked")
// 定义图表处理器，负责轴格式化和结果组装
public class FunnelHandler extends ExtQuotaChartHandler {
    @Getter
    private String type = "funnel";

    @Override
    // 初始化当前处理器的类型和默认配置
    public void init() {
        chartHandlerManager.registerChartHandler(this.getRender(), this.getType(), this);
        chartHandlerManager.registerChartHandler(this.getRender(), "stage-funnel", this);
    }
}
