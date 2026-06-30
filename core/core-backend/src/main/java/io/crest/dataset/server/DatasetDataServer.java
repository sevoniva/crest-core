package io.crest.dataset.server;

import io.crest.api.dataset.DatasetDataApi;
import io.crest.api.dataset.dto.*;
import io.crest.api.dataset.union.DatasetGroupInfoDTO;
import io.crest.dataset.manage.DatasetDataManage;
import io.crest.extensions.datasource.dto.DatasetTableDTO;
import io.crest.extensions.datasource.dto.DatasetTableFieldDTO;
import io.crest.utils.LogUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("dataset-data")
/**
 * 数据集数据接口控制器，负责预览、枚举和统计数据查询
 */
public class DatasetDataServer implements DatasetDataApi {
    @Resource
    private DatasetDataManage datasetDataManage;

    /**
     * 预览数据集前 100 条数据
     */
    @Override
    public Map<String, Object> previewData(DatasetGroupInfoDTO datasetGroupInfoDTO) throws Exception {
        return datasetDataManage.previewDataWithLimit(datasetGroupInfoDTO, 0, 100, false, true);
    }

    /**
     * 查询数据表字段信息
     */
    @Override
    public List<DatasetTableFieldDTO> tableField(DatasetTableDTO datasetTableDTO) throws Exception {
        return datasetDataManage.getTableFields(datasetTableDTO);
    }

    /**
     * 执行 SQL 预览并记录日志
     */
    @Override
    public Map<String, Object> previewSql(PreviewSqlDTO dto) throws Exception {
        return datasetDataManage.previewSqlWithLog(dto);
    }

    /**
     * 执行 SQL 预览校验
     */
    @Override
    public Map<String, Object> previewSqlCheck(PreviewSqlDTO dto) throws Exception {
        return datasetDataManage.previewSql(dto);
    }

    /**
     * 获取数据集字段的枚举值
     */
    @Override
    public List<String> getFieldEnumDs(EnumObj map) throws Exception {
        try {
            return datasetDataManage.getFieldEnumDs(map);
        } catch (Exception e) {
            LogUtil.info(e.getMessage());
            return null;
        }
    }

    /**
     * 按多个字段值请求获取字段枚举值
     */
    @Override
    public List<String> getFieldEnum(MultFieldValuesRequest multFieldValuesRequest) {
        try {
            return datasetDataManage.getFieldEnum(multFieldValuesRequest);
        } catch (Exception e) {
            LogUtil.info(e.getMessage());
            return null;
        }
    }

    /**
     * 获取字段枚举对象列表
     */
    @Override
    public List<Map<String, Object>> getFieldEnumObj(EnumValueRequest request) throws Exception {
        try {
            return datasetDataManage.getFieldEnumObj(request);
        } catch (Exception e) {
            LogUtil.info(e.getMessage());
            return null;
        }
    }

    /**
     * 统计数据集基础数据量
     */
    @Override
    public Long datasetCount(DatasetGroupInfoDTO datasetGroupInfoDTO) throws Exception {
        return datasetDataManage.datasetTotal(datasetGroupInfoDTO.getId());
    }

    /**
     * 统计带筛选条件的数据集数据量
     */
    @Override
    public Long datasetTotal(DatasetGroupInfoDTO datasetGroupInfoDTO) throws Exception {
        return datasetDataManage.datasetCountWithWhere(datasetGroupInfoDTO.getId());
    }

    /**
     * 获取字段值树结构
     */
    @Override
    public List<BaseTreeNodeDTO> getFieldValueTree(MultFieldValuesRequest multFieldValuesRequest) throws Exception {
        try {
            return datasetDataManage.getFieldValueTree(multFieldValuesRequest);
        } catch (Exception e) {
            LogUtil.info(e.getMessage());
            return null;
        }
    }
}
