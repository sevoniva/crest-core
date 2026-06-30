package io.crest.dataset.server;

import io.crest.api.dataset.DatasetTableSqlLogApi;
import io.crest.api.dataset.dto.SqlLogDTO;
import io.crest.dataset.manage.DatasetTableSqlLogManage;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("dataset-table-sql-log")
// 实现接口服务，衔接业务处理和返回结果
public class DatasetTableSqlLogServer implements DatasetTableSqlLogApi {
    @Resource
    private DatasetTableSqlLogManage datasetTableSqlLogManage;

    @Override
    // 保存当前业务数据
    public void save(SqlLogDTO sqlLogDTO) throws Exception {
        datasetTableSqlLogManage.save(sqlLogDTO);
    }

    @Override
    // 查询当前业务数据列表
    public List<SqlLogDTO> listByTableId(SqlLogDTO sqlLogDTO) throws Exception {
        return datasetTableSqlLogManage.listByTableId(sqlLogDTO);
    }

    @Override
    public void tableRemoval(String id) throws Exception {
        datasetTableSqlLogManage.tableRemoval(id);
    }
}
