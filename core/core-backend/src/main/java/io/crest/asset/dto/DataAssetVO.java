package io.crest.asset.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
// 定义接口请求或返回数据的传输结构
public class DataAssetVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 3935874682962965418L;

    private String assetType;
    private String assetTypeLabel;
    private String assetId;
    private String name;
    private String extraType;
    private String parentAssetType;
    private String parentAssetId;
    private String description;
    private String tags;
    private Boolean certified;
    private Boolean recommended;
    private Boolean deprecated;
    private Boolean canManage;
    private Integer upstreamCount;
    private Integer downstreamCount;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerId;
    private String ownerName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long creatorId;
    private String creatorName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long orgId;
    private String orgName;

    private Long createTime;
    private Long updateTime;
}
