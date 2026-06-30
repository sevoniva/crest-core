package io.crest.asset.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
// 定义接口请求或返回数据的传输结构
public class DataAssetRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 3712526211474504967L;

    private String keyword;
    private String assetType;
    private Boolean certified;
    private Boolean recommended;
    private Boolean deprecated;
    private Long ownerId;
}
