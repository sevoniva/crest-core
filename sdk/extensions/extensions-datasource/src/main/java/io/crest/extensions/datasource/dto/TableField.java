package io.crest.extensions.datasource.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;


@Data
// 定义接口请求或返回数据的传输结构
public class TableField implements Serializable {
    private String name;
    private String originName;
    private String dbFieldName;
    private String type;               //SQL type from java.sql.Types
    private int precision;
    private long size;
    private int scale;
    private String length;
    private boolean checked = false;
    private boolean primaryKey = false;
    private String nativeType;
    private Integer fieldType;
    private Integer extractedFieldType;
    private int extField;
    private String jsonPath;
    private boolean primary;
    private boolean autoIncrement;
    List<Object> value;

    private int inCount;
    private String term = "eq";
    private Integer typeNumber;

}
