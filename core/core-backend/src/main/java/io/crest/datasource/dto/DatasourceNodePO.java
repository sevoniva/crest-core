package io.crest.datasource.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
// 定义接口请求或返回数据的传输结构
public class DatasourceNodePO implements Serializable {

    @Serial
    private static final long serialVersionUID = -4457506330575500164L;

    private Long id;
    private String name;
    private String type;
    private Long pid;
}
