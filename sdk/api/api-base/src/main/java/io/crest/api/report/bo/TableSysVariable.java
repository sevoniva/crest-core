package io.crest.api.report.bo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Data
// 定义模块接口契约和数据传输结构
public class TableSysVariable implements Serializable {

    private Long tableId;

    private List<String> sysVariables;
}
