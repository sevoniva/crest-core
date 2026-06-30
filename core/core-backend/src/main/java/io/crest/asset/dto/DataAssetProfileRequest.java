package io.crest.asset.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
// 定义接口请求或返回数据的传输结构
public class DataAssetProfileRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = -8590854662651660145L;

    private String assetType;
    private String assetId;
    private String description;
    private Long ownerId;
    private Boolean certified;
    private Boolean recommended;
    private Boolean deprecated;
    private String tags;
}
