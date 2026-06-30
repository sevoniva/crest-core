package io.crest.constant;

import java.util.Arrays;

// 定义当前业务支持的枚举取值
public enum DataFillingFinishTaskEnum {

    OPEN(0), FINISHED(1);

    private Integer flag;

    public Integer getFlag() {
        return flag;
    }

    public void setFlag(Integer flag) {
        this.flag = flag;
    }

    DataFillingFinishTaskEnum(Integer flag) {
        this.flag = flag;
    }

    DataFillingFinishTaskEnum() {
    }

    public static DataFillingFinishTaskEnum fromValue(Integer flag) {
        return Arrays.stream(values()).filter(v -> v.flag.equals(flag)).findFirst().get();
    }
}
