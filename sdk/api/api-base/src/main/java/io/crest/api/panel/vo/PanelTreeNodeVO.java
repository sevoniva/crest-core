package io.crest.api.panel.vo;

import lombok.Data;

import java.io.Serializable;

@Data
// 定义页面展示或接口返回的数据结构
public class PanelTreeNodeVO implements Serializable {

    private Long id;

    private String name;

    private String type;
}
