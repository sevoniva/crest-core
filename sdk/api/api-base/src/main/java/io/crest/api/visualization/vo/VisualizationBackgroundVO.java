package io.crest.api.visualization.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 可视化背景资源视图对象。
 */
@Data
public class VisualizationBackgroundVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    private String name;

    private String classification;

    private String content;

    private String remark;

    private Integer sort;

    private Long uploadTime;

    private String baseUrl;

    private String url;

}
