package io.crest.api.sync.task.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * 数据同步表字段描述。
 */
@Setter
@Getter
@EqualsAndHashCode(callSuper = false)
public class TableField {
    private String fieldSource;
    private String fieldName;
    private String remarks;
    private String fieldType;
    private int fieldSize;
    /**
     * 精度
     */
    private int fieldPrecision;

    private boolean fieldPk;
    private boolean fieldIndex;
    private Object defaultValue;

}
