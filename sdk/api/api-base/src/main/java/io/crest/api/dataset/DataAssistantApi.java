package io.crest.api.dataset;

import io.crest.api.dataset.vo.DataSQLBotAssistantVO;
import io.crest.api.dataset.vo.DataSQLBotDatasetVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

// 定义模块接口契约和数据传输结构
public interface DataAssistantApi {
    @GetMapping("/datasource")
    List<DataSQLBotAssistantVO> datasourceList(@RequestParam(required = false) Long dsId, @RequestParam(required = false) Long tableId);

    @GetMapping("/dataset/{dvInfo}")
    List<DataSQLBotDatasetVO> datasetList(@PathVariable String dvInfo);
}
