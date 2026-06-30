package io.crest.api.sync.datasource.dto;

import lombok.Data;

/**
 * 数据同步数据源传输对象。
 */
@Data
public class SyncDatasourceDTO {

    /**
     * ID
     */
    private String id;

    /**
     * 名称
     */
    private String name;

    /**
     * 描述
     */
    private String desc;

    /**
     * 类型
     */
    private String type;

    /**
     * 详细信息
     */
    private String configuration;

    /**
     * Create timestamp
     */
    private Long createTime;

    /**
     * Update timestamp
     */
    private Long updateTime;

    /**
     * 创建人ID
     */
    private Long createBy;
    private String createByUserName;

    /**
     * 状态
     */
    private String status;

    private String statusRemark;


}
