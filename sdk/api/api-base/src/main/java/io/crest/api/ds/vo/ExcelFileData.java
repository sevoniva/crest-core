package io.crest.api.ds.vo;

import lombok.Data;

import java.util.List;

@Data
// 定义页面展示或接口返回的数据结构
public class ExcelFileData {
    private String id;
    private String excelLabel;
    private List<ExcelSheetData> sheets;
    private String path;
    private boolean isSheet = false;
}
