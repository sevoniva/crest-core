package io.crest.api.ds.vo;

import lombok.Data;

import java.util.List;

@Data
// 定义页面展示或接口返回的数据结构
public class ExcelConfiguration {
    private String url;
    private List<ExcelSheetData> sheets;
    private String userName;
    private String passwd;
}
