package io.crest.extensions.view.dto;


import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
// 定义接口请求或返回数据的传输结构
public class DatasetRowPermissionsTreeObj implements Serializable {

    private String logic;
    private List<DatasetRowPermissionsTreeItem> items;

    private static final long serialVersionUID = 1L;
}
