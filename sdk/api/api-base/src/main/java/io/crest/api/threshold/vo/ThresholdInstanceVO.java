package io.crest.api.threshold.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
// 定义页面展示或接口返回的数据结构
public class ThresholdInstanceVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 4896658954041017L;

    private Long id;

    private Long taskId;

    private String name;

    private Long execTime;

    private Boolean status;

    private String content;

    private String msg;
}
