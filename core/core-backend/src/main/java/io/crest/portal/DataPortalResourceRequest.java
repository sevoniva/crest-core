package io.crest.portal;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
// 定义接口请求或返回数据的传输结构
public class DataPortalResourceRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1619800890641273928L;

    private String keyword;
    private String type;
    private Integer page;
    private Integer pageSize;
    private Boolean asc;
    private String queryFrom;
}
