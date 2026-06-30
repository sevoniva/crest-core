package io.crest.api.chart.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@AllArgsConstructor
@NoArgsConstructor
@Data
// 定义页面展示或接口返回的数据结构
public class ThresholdCheckVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 2782520137890522432L;

    private boolean valid;

    private String content;

    private String msg;

    private Long taskId;
}
