package io.crest.extensions.view.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.List;

@Data
// 定义接口请求或返回数据的传输结构
public class SqlVariableDetails {
    private String variableName;
    private String alias;
    private List<String> type;
    private int fieldType;
    private String details;
    private String defaultValue;
    private DefaultValueScope defaultValueScope;
    @JsonSerialize(using = ToStringSerializer.class)
    private String id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long datasetTableId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long datasetGroupId;
    private boolean required;
    private String operator;
    private List<String> value;
    private String datasetFullName;
    // 定义当前业务支持的枚举取值
    public enum DefaultValueScope {
        EDIT("EDIT"),
        ALLSCOPE("ALLSCOPE");
        private String  type;
        DefaultValueScope(String type){
            this.type = type;
        }
        public String getType(){
            return type;
        }
    }
}
