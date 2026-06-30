package io.crest.chart.charts.impl.bar;

import lombok.Getter;
import org.springframework.stereotype.Component;

@Component
@SuppressWarnings("unchecked")
// 定义图表处理器，负责轴格式化和结果组装
public class BidirectionalBarHandler extends ProgressBarHandler {
    @Getter
    private String type = "bidirectional-bar";
}
