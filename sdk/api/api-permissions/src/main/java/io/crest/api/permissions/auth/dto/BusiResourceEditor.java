package io.crest.api.permissions.auth.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
// 定义接口请求或返回数据的传输结构
public class BusiResourceEditor implements Serializable {

    @Serial
    private static final long serialVersionUID = 5193320462388120821L;
    private Long id;
    private String name;
    private String flag;
    private int extraFlag;
    private int extraFlag1;
}
