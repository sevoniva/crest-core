package io.crest.portal;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
// 定义页面展示或接口返回的数据结构
public class DataPortalResourceVO implements Serializable {
    @Serial
    private static final long serialVersionUID = -8642865109750149585L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String name;
    private String type;
    private String creatorName;
    private Long updateTime;
    private Integer chartCount;
    private Integer extFlag;
    private Boolean favorite;
    private Boolean certified;
    private Boolean recommended;
    private Boolean deprecated;
}
