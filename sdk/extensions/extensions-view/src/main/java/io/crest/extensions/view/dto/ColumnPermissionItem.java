package io.crest.extensions.view.dto;

import lombok.Data;

import java.util.List;

@Data
// 定义接口请求或返回数据的传输结构
public class ColumnPermissionItem {
    private Long id;
    private String name;
    private Integer fieldType;
    private Boolean selected = false;
    private String opt;

    private DesensitizationRule desensitizationRule;


    @Data
    // 定义接口请求或返回数据的传输结构
    public class DesensitizationRule {
        private BuiltInRule builtInRule;
        private CustomBuiltInRule customBuiltInRule;

        private Integer m;
        private Integer n;
        private String specialCharacter;
        private List<String> specialCharacterList;
    }

    // 定义当前业务支持的枚举取值
    public enum BuiltInRule {
        CompleteDesensitization,
        KeepFirstAndLastThreeCharacters,
        KeepMiddleThreeCharacters,
        custom
    }

    static public String CompleteDesensitization = "******";
    static public String KeepFirstAndLastThreeCharacters = "XXX***XXX";
    static public String KeepMiddleThreeCharacters = "***XXX***";
    // 定义当前业务支持的枚举取值
    public enum CustomBuiltInRule {
        RetainBeforeMAndAfterN,
        RetainMToN
    }
}
