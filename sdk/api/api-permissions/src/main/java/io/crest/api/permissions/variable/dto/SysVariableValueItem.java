package io.crest.api.permissions.variable.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
// 定义接口请求或返回数据的传输结构
public class SysVariableValueItem {
    private String variableValue;
    @JsonSerialize(using = ToStringSerializer.class)
    private String variableValue2;
    private String variableType;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long variableId;
    private List<String> variableValueIds = new ArrayList<>();
    @JsonSerialize(using = ToStringSerializer.class)
    private Long variableValueId ;
    private String variableName;
    private boolean valid = true;
    private List<SysVariableValueDto> valueList;
    private SysVariableDto sysVariableDto;

}
