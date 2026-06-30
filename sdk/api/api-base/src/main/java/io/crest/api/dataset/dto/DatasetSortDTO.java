package io.crest.api.dataset.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
// 定义接口请求或返回数据的传输结构
public class DatasetSortDTO implements Serializable {

    private Long id;

    private String name;

    private String sort;

    private List<String> custom;
}
