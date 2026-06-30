package io.crest.chart.charts.impl.others;

import io.crest.chart.charts.impl.YoyChartHandler;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
@SuppressWarnings("unchecked")
// 定义图表处理器，负责轴格式化和结果组装
public class RichTextHandler extends YoyChartHandler {
    @Getter
    private String type = "rich-text";
    @Getter
    private String render = "custom";
}
