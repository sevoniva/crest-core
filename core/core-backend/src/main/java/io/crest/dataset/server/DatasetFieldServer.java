package io.crest.dataset.server;

import io.crest.api.dataset.DatasetTableApi;
import io.crest.api.dataset.dto.MultFieldValuesRequest;
import io.crest.api.dataset.engine.SQLFunctionDTO;
import io.crest.api.dataset.engine.SQLFunctionsEnum;
import io.crest.dataset.manage.DatasetDataManage;
import io.crest.dataset.manage.DatasetTableFieldManage;
import io.crest.dataset.utils.DatasetUtils;
import io.crest.extensions.datasource.dto.DatasetTableFieldDTO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("dataset-field")
// 提供数据集字段维护、权限字段和函数列表接口
public class DatasetFieldServer implements DatasetTableApi {
    @Resource
    private DatasetTableFieldManage datasetTableFieldManage;
    @Resource
    private DatasetDataManage datasetDataManage;

    // 保存图表字段配置
    @Override
    public DatasetTableFieldDTO save(DatasetTableFieldDTO datasetTableFieldDTO) throws Exception {
        return datasetTableFieldManage.chartFieldSave(datasetTableFieldDTO);
    }

    // 查询单个数据集字段
    @Override
    public DatasetTableFieldDTO get(Long id) {
        return datasetTableFieldManage.selectById(id);
    }

    // 查询数据集下的字段列表
    @Override
    public List<DatasetTableFieldDTO> listByDatasetGroup(Long id) {
        List<DatasetTableFieldDTO> datasetTableFieldDTOS = datasetTableFieldManage.selectByDatasetGroupId(id);
        DatasetUtils.listEncode(datasetTableFieldDTOS);
        return datasetTableFieldDTOS;
    }

    // 按数据集 ID 批量查询字段列表
    @Override
    public Map<String, List<DatasetTableFieldDTO>> listByDsIds(List<Long> ids) {
        return datasetTableFieldManage.selectByDatasetGroupIds(ids);
    }

    // 删除数据集字段
    @Override
    public void delete(Long id) {
        datasetTableFieldManage.deleteById(id);
    }

    // 查询维度和指标字段分组
    @Override
    public Map<String, List<DatasetTableFieldDTO>> listByDQ(Long id) {
        return datasetTableFieldManage.listByDQ(id);
    }

    // 查询智能助手可用字段
    @Override
    public Map<String, List<DatasetTableFieldDTO>> copilotFields(Long id) throws Exception {
        return datasetTableFieldManage.copilotFields(id);
    }

    // 查询带权限信息的数据集字段
    @Override
    public List<DatasetTableFieldDTO> listFieldsWithPermissions(Long id) {
        return datasetTableFieldManage.listFieldsWithPermissionsRemoveAgg(id);
    }

    // 批量查询权限字段枚举值
    @Override
    public List<String> multFieldValuesForPermissions(@RequestBody MultFieldValuesRequest multFieldValuesRequest) throws Exception {
        return datasetDataManage.getFieldEnum(multFieldValuesRequest);
    }

    // 查询内置 SQL 函数列表
    @Override
    public List<SQLFunctionDTO> functions() {
        SQLFunctionsEnum[] values = SQLFunctionsEnum.values();
        return Arrays.stream(values).map(ele -> {
            SQLFunctionDTO dto = new SQLFunctionDTO();
            dto.setName(ele.getName());
            dto.setFunc(ele.getFunc());
            dto.setType(ele.getType());
            dto.setDesc(ele.getDesc());
            dto.setCustom(ele.isCustom());
            return dto;
        }).collect(Collectors.toList());
    }

    // 删除图表引用字段
    @Override
    public void deleteChartFields(Long id) {
        datasetTableFieldManage.deleteChartFields(id);
    }
}
