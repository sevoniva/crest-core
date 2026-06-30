package io.crest.api.ds.vo;

import io.crest.extensions.datasource.dto.TableField;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
// 定义页面展示或接口返回的数据结构
public class ExcelSheetData {
    private String excelLabel;
    private List<String[]> data;
    private List<TableField> fields;
    private String tableName;
    private String fileName;
    private String size;
    private String displayTableName;
    private Long lastUpdateTime;
    private String path;
    private boolean isSheet = true;
    private String sheetId;
    private String sheetExcelId;
    private List<Map<String, Object>> jsonArray;
    private boolean newSheet;
    private String inspectionStatus;
    private String inspectionMessage;

}
