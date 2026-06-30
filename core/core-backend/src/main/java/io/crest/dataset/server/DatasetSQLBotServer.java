package io.crest.dataset.server;

import io.crest.api.dataset.DataAssistantApi;
import io.crest.api.dataset.vo.DataSQLBotAssistantVO;
import io.crest.api.dataset.vo.DataSQLBotDatasetVO;
import io.crest.dataset.manage.DatasetSQLBotManage;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/sqlbot")
@ConditionalOnProperty(prefix = "crest.feature.sqlbot", name = "enabled", havingValue = "true")
// 实现接口服务，衔接业务处理和返回结果
public class DatasetSQLBotServer implements DataAssistantApi {

    @Resource
    private DatasetSQLBotManage datasetSQLBotManage;
    @Override
    public List<DataSQLBotAssistantVO> datasourceList(Long dsId, Long tableId) {
        return datasetSQLBotManage.datasourceList(dsId, tableId);
    }

    @Override
    public List<DataSQLBotDatasetVO> datasetList(String dvInfo) {
        return datasetSQLBotManage.datasetList(dvInfo);
    }
}
