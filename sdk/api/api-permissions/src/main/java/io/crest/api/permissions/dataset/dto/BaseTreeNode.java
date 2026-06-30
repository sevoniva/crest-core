package io.crest.api.permissions.dataset.dto;


import io.crest.model.ITreeBase;
import lombok.Data;

import java.util.List;

@Data
// 定义接口请求或返回数据的传输结构
public class BaseTreeNode implements ITreeBase<BaseTreeNode> {

    private Long id;

    private Long pid;

    private String text;

    private String nodeType;

    private List<BaseTreeNode> children;

    public BaseTreeNode(Long id, Long pid, String text, String nodeType) {
        this.id = id;
        this.pid = pid;
        this.text = text;
        this.nodeType = nodeType;
    }

}
