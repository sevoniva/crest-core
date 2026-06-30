package io.crest.api.permissions.dataset.dto;


import lombok.Data;

@Data
// 定义接口请求或返回数据的传输结构
public class WhiteListUsersRequest {
    private Long authTargetId;
    private String authTargetType;
    private Long datasetId;
}
