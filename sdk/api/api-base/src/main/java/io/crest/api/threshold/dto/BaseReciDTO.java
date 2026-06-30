package io.crest.api.threshold.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
// 定义接口请求或返回数据的传输结构
public class BaseReciDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = -1996467050556455121L;

    private List<Integer> reciFlagList;

    private List<String> uidList;

    private List<String> ridList;

    private List<String> emailList;

    private List<String> larkGroupList;

    private List<String> larksuiteGroupList;

    private List<String> webhookList;
}
