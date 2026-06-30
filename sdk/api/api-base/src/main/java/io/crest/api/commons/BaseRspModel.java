package io.crest.api.commons;


import lombok.Data;

import java.io.Serializable;

@Data
// 定义模块接口契约和数据传输结构
public class BaseRspModel implements Serializable {

    private Boolean success = true;

    private String requestId;

    private Object responseInfo;


}
