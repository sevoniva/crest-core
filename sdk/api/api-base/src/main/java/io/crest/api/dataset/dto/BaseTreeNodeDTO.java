package io.crest.api.dataset.dto;


import lombok.Data;

import java.util.List;

@Data
// 定义接口请求或返回数据的传输结构
public class BaseTreeNodeDTO {

    private String id;

    private String pid;

    private String text;

    private String nodeType;

    private List<BaseTreeNodeDTO> children;

    public BaseTreeNodeDTO(String id, String pid, String text, String nodeType) {
        this.id = id;
        this.pid = pid;
        this.text = text;
        this.nodeType = nodeType;
    }

}
