package io.crest.dataset.manage;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import io.crest.api.dataset.union.DatasetGroupInfoDTO;
import io.crest.dataset.dao.auto.entity.CoreDatasetTableField;
import io.crest.dataset.dao.auto.mapper.CoreDatasetTableFieldMapper;
import io.crest.dataset.utils.DatasetUtils;
import io.crest.dataset.utils.TableUtils;
import io.crest.engine.constant.ExtFieldConstant;
import io.crest.engine.func.FunctionConstant;
import io.crest.engine.utils.Utils;
import io.crest.exception.CrestException;
import io.crest.extensions.datasource.api.PluginManageApi;
import io.crest.extensions.datasource.dto.CalParam;
import io.crest.extensions.datasource.dto.DatasetTableFieldDTO;
import io.crest.extensions.datasource.dto.DatasourceSchemaDTO;
import io.crest.extensions.datasource.dto.FieldGroupDTO;
import io.crest.extensions.datasource.model.SQLObj;
import io.crest.extensions.view.dto.ColumnPermissionItem;
import io.crest.i18n.Translator;
import io.crest.utils.AuthUtils;
import io.crest.utils.BeanUtils;
import io.crest.utils.IDUtils;
import io.crest.utils.JsonUtil;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据集字段管理组件，负责字段保存、删除、权限过滤和 DTO 转换
 */
@Component
@Transactional
@SuppressWarnings("unchecked")
public class DatasetTableFieldManage {
    /**
     * 数据集字段表的基础 Mapper
     */
    @Resource
    private CoreDatasetTableFieldMapper coreDatasetTableFieldMapper;
    /**
     * 字段权限管理器，用于按用户过滤字段可见性
     */
    @Resource
    private PermissionManage permissionManage;
    /**
     * 数据集 SQL 管理器，用于获取联合数据集编辑 SQL
     */
    @Resource
    private DatasetSQLManage datasetSQLManage;
    /**
     * 数据集分组管理器，用于读取数据集分组上下文
     */
    @Resource
    private DatasetGroupManage datasetGroupManage;
    /**
     * 可选数据源插件管理器，用于计算字段表达式解析
     */
    @Autowired(required = false)
    private PluginManageApi pluginManage;

    /**
     * 保存基础字段记录，按主键存在性决定新增或更新
     */
    public void save(CoreDatasetTableField coreDatasetTableField) {
        checkNameLength(coreDatasetTableField.getName());
        if (ObjectUtils.isEmpty(coreDatasetTableField.getId())) {
            coreDatasetTableField.setId(IDUtils.snowID());
            coreDatasetTableFieldMapper.insert(coreDatasetTableField);
        } else {
            coreDatasetTableFieldMapper.updateById(coreDatasetTableField);
        }
    }

    /**
     * 保存图表计算字段，并校验同图表下字段名称是否重复
     */
    public DatasetTableFieldDTO chartFieldSave(DatasetTableFieldDTO datasetTableFieldDTO) {
        checkNameLength(datasetTableFieldDTO.getName());
        CoreDatasetTableField coreDatasetTableField = coreDatasetTableFieldMapper.selectById(datasetTableFieldDTO.getId());
        QueryWrapper<CoreDatasetTableField> wrapper = new QueryWrapper<>();
        wrapper.eq("name", datasetTableFieldDTO.getName());
        wrapper.eq("chart_id", datasetTableFieldDTO.getChartId());
        if (ObjectUtils.isNotEmpty(coreDatasetTableField)) {
            wrapper.ne("id", datasetTableFieldDTO.getId());
        }
        List<CoreDatasetTableField> fields = coreDatasetTableFieldMapper.selectList(wrapper);
        if (ObjectUtils.isNotEmpty(fields)) {
            CrestException.throwException(Translator.get("i18n_field_name_duplicated"));
        }
        datasetTableFieldDTO.setDatasetGroupId(null);
        return save(datasetTableFieldDTO);
    }

    /**
     * 数据集保存时使用
     *
     * @param datasetTableFieldDTO
     * @return
     */
    public DatasetTableFieldDTO save(DatasetTableFieldDTO datasetTableFieldDTO) {
        checkNameLength(datasetTableFieldDTO.getName());
        CoreDatasetTableField coreDatasetTableField = coreDatasetTableFieldMapper.selectById(datasetTableFieldDTO.getId());
        CoreDatasetTableField record = transDTO2Record(datasetTableFieldDTO);
        if (ObjectUtils.isEmpty(record.getEngineFieldName())) {
            String n = TableUtils.fieldNameShort(record.getId() + "");
            record.setFieldShortName(n);
            record.setEngineFieldName(n);
        }
        if (ObjectUtils.isEmpty(coreDatasetTableField)) {
            coreDatasetTableFieldMapper.insert(record);
        } else {
            coreDatasetTableFieldMapper.updateById(record);
        }
        return datasetTableFieldDTO;
    }

    /**
     * 保存字段 DTO 到基础字段表
     */
    public DatasetTableFieldDTO saveField(DatasetTableFieldDTO datasetTableFieldDTO) {
        CoreDatasetTableField record = new CoreDatasetTableField();
        if (ObjectUtils.isEmpty(datasetTableFieldDTO.getId())) {
            datasetTableFieldDTO.setId(IDUtils.snowID());
            BeanUtils.copyBean(record, datasetTableFieldDTO);
            coreDatasetTableFieldMapper.insert(record);
        } else {
            BeanUtils.copyBean(record, datasetTableFieldDTO);
            coreDatasetTableFieldMapper.updateById(record);
        }
        return datasetTableFieldDTO;
    }

    /**
     * 查询指定图表下的计算字段列表
     */
    public List<DatasetTableFieldDTO> getChartCalcFields(Long chartId) {
        QueryWrapper<CoreDatasetTableField> wrapper = new QueryWrapper<>();
        wrapper.eq("chart_id", chartId);
        return transDTO(coreDatasetTableFieldMapper.selectList(wrapper));
    }

    /**
     * 根据字段 ID 删除字段记录
     */
    public void deleteById(Long id) {
        coreDatasetTableFieldMapper.deleteById(id);
    }

    /**
     * 数据集表更新时，删除不在保留列表中的字段
     */
    public void deleteByDatasetTableUpdate(Long datasetTableId, List<Long> fieldIds) {
        if (!CollectionUtils.isEmpty(fieldIds)) {
            QueryWrapper<CoreDatasetTableField> wrapper = new QueryWrapper<>();
            wrapper.eq("dataset_table_id", datasetTableId);
            wrapper.notIn("id", fieldIds);
            coreDatasetTableFieldMapper.delete(wrapper);
        }
    }

    /**
     * 数据集分组更新时，删除不在保留列表中的非图表复制字段
     */
    public void deleteByDatasetGroupUpdate(Long datasetGroupId, List<Long> fieldIds) {
        if (!CollectionUtils.isEmpty(fieldIds)) {
            // chartCopyFields不删除
            List<Long> chartCopyFields = getChartCopyFieldsByDatasetGroupIdAndOriginIds(datasetGroupId, fieldIds);
            fieldIds.addAll(chartCopyFields);
            QueryWrapper<CoreDatasetTableField> wrapper = new QueryWrapper<>();
            wrapper.eq("dataset_group_id", datasetGroupId);
            wrapper.notIn("id", fieldIds);
            coreDatasetTableFieldMapper.delete(wrapper);
        }
    }

    /**
     * 获取图表复制字段
     * 通过原始名是[field_id]格式，ext_field 为 2，并且chart_id不为空的字段判定为图表复制字段
     * @param datasetGroupId 数据集ID
     * @param fieldIds 原数据集字段ID列表
     */
    public List<Long> getChartCopyFieldsByDatasetGroupIdAndOriginIds(Long datasetGroupId, List<Long> fieldIds) {
        List<String> originNames = fieldIds.stream().map(id -> "[" + id + "]").collect(Collectors.toList());
        QueryWrapper<CoreDatasetTableField> wrapper = new QueryWrapper<>();
        wrapper.eq("dataset_group_id", datasetGroupId);
        wrapper.eq("ext_field", 2);
        wrapper.in("origin_name", originNames);
        wrapper.isNotNull("chart_id");
        List<CoreDatasetTableField> list = coreDatasetTableFieldMapper.selectList(wrapper);
        return list.stream().map(CoreDatasetTableField::getId).collect(Collectors.toList());
    }

    /**
     * 数据集分组删除时，清理该分组下全部字段
     */
    public void deleteByDatasetGroupDelete(Long datasetGroupId) {
        QueryWrapper<CoreDatasetTableField> wrapper = new QueryWrapper<>();
        wrapper.eq("dataset_group_id", datasetGroupId);
        coreDatasetTableFieldMapper.delete(wrapper);
    }

    /**
     * 删除指定图表下的字段
     */
    public void deleteChartFields(Long chartId) {
        QueryWrapper<CoreDatasetTableField> wrapper = new QueryWrapper<>();
        wrapper.eq("chart_id", chartId);
        coreDatasetTableFieldMapper.delete(wrapper);
    }

    /**
     * 查询指定数据集表下的字段列表
     */
    public List<DatasetTableFieldDTO> selectByDatasetTableId(Long id) {
        QueryWrapper<CoreDatasetTableField> wrapper = new QueryWrapper<>();
        wrapper.eq("dataset_table_id", id);
        return transDTO(coreDatasetTableFieldMapper.selectList(wrapper));
    }

    /**
     * 查询指定数据集分组下已勾选的基础字段
     */
    public List<DatasetTableFieldDTO> selectByDatasetGroupId(Long id) {
        QueryWrapper<CoreDatasetTableField> wrapper = new QueryWrapper<>();
        wrapper.eq("dataset_group_id", id);
        wrapper.eq("checked", true);
        wrapper.isNull("chart_id");
        return transDTO(coreDatasetTableFieldMapper.selectList(wrapper));
    }

    /**
     * 批量查询多个数据集分组下的已勾选基础字段
     */
    public Map<String, List<DatasetTableFieldDTO>> selectByDatasetGroupIds(List<Long> ids) {
        Map<String, List<DatasetTableFieldDTO>> map = new HashMap<>();
        for (Long id : ids) {
            QueryWrapper<CoreDatasetTableField> wrapper = new QueryWrapper<>();
            wrapper.eq("dataset_group_id", id);
            wrapper.eq("checked", true);
            wrapper.isNull("chart_id");
            wrapper.eq("ext_field", 0);
            map.put(String.valueOf(id), transDTO(coreDatasetTableFieldMapper.selectList(wrapper)));
        }
        return map;
    }

    /**
     * 根据字段 ID 列表查询字段 DTO
     */
    public List<DatasetTableFieldDTO> selectByFieldIds(List<Long> ids) {
        QueryWrapper<CoreDatasetTableField> wrapper = new QueryWrapper<>();
        wrapper.in("id", ids);
        return transDTO(coreDatasetTableFieldMapper.selectList(wrapper));
    }

    /**
     * 根据字段 ID 查询单个字段 DTO
     */
    public DatasetTableFieldDTO selectById(Long id) {
        CoreDatasetTableField coreDatasetTableField = coreDatasetTableFieldMapper.selectById(id);
        if (coreDatasetTableField == null) return null;
        return transObj(coreDatasetTableField);
    }

    /**
     * 返回维度、指标列表
     *
     * @return
     */
    public Map<String, List<DatasetTableFieldDTO>> listByDQ(Long id) {
        QueryWrapper<CoreDatasetTableField> wrapper = new QueryWrapper<>();
        wrapper.eq("dataset_group_id", id);
        wrapper.eq("checked", true);
        List<DatasetTableFieldDTO> list = transDTO(coreDatasetTableFieldMapper.selectList(wrapper));
        List<DatasetTableFieldDTO> dimensionList = list.stream().filter(ele -> Strings.CI.equals(ele.getGroupType(), "d")).collect(Collectors.toList());
        List<DatasetTableFieldDTO> quotaList = list.stream().filter(ele -> Strings.CI.equals(ele.getGroupType(), "q")).collect(Collectors.toList());
        Map<String, List<DatasetTableFieldDTO>> map = new LinkedHashMap<>();
        map.put("dimensionList", dimensionList);
        map.put("quotaList", quotaList);
        return map;
    }

    /**
     * 查询 Copilot 可使用的维度和指标字段，并应用列权限过滤
     */
    public Map<String, List<DatasetTableFieldDTO>> copilotFields(Long id) throws Exception {
        DatasetGroupInfoDTO datasetGroupInfoDTO = datasetGroupManage.datasetGroupInfoDTO(id, null);
        Map<String, Object> sqlMap = datasetSQLManage.getUnionSQLForEdit(datasetGroupInfoDTO, null);
        Map<Long, DatasourceSchemaDTO> dsMap = (Map<Long, DatasourceSchemaDTO>) sqlMap.get("dsMap");
        boolean crossDs = Utils.isCrossDs(dsMap);
        if (crossDs) {
            CrestException.throwException(Translator.get("i18n_dataset_cross_error"));
        }
        if (!isCopilotSupport(dsMap)) {
            CrestException.throwException(Translator.get("i18n_copilot_ds"));
        }

        QueryWrapper<CoreDatasetTableField> wrapper = new QueryWrapper<>();
        wrapper.eq("dataset_group_id", id);
        wrapper.eq("checked", true);
        wrapper.eq("ext_field", 0);
        List<DatasetTableFieldDTO> list = transDTO(coreDatasetTableFieldMapper.selectList(wrapper));

        Map<String, ColumnPermissionItem> desensitizationList = new HashMap<>();
        list = permissionManage.filterColumnPermissions(list, desensitizationList, id, null);

        List<DatasetTableFieldDTO> dimensionList = list.stream().filter(ele -> Strings.CI.equals(ele.getGroupType(), "d")).collect(Collectors.toList());
        List<DatasetTableFieldDTO> quotaList = list.stream().filter(ele -> Strings.CI.equals(ele.getGroupType(), "q")).collect(Collectors.toList());
        Map<String, List<DatasetTableFieldDTO>> map = new LinkedHashMap<>();
        map.put("dimensionList", dimensionList);
        map.put("quotaList", quotaList);
        return map;
    }

    /**
     * 查询带列权限和脱敏标记的字段列表
     */
    public List<DatasetTableFieldDTO> listFieldsWithPermissions(Long id) {
        List<DatasetTableFieldDTO> fields = selectByDatasetGroupId(id);
        Map<String, ColumnPermissionItem> desensitizationList = new HashMap<>();
        Long userId = AuthUtils.getUser() == null ? null : AuthUtils.getUser().getUserId();
        List<DatasetTableFieldDTO> tmp = permissionManage
                .filterColumnPermissions(fields, desensitizationList, id, userId)
                .stream()
                .sorted(Comparator.comparing(DatasetTableFieldDTO::getGroupType))
                .toList();
        tmp.forEach(ele -> ele.setDesensitized(desensitizationList.containsKey(ele.getEngineFieldName())));
        return tmp;
    }

    /**
     * 查询带权限的字段列表，并剔除聚合函数型计算字段
     */
    public List<DatasetTableFieldDTO> listFieldsWithPermissionsRemoveAgg(Long id) {
        List<DatasetTableFieldDTO> fields = selectByDatasetGroupId(id);
        Map<String, ColumnPermissionItem> desensitizationList = new HashMap<>();
        Long userId = AuthUtils.getUser() == null ? null : AuthUtils.getUser().getUserId();
        SQLObj tableObj = new SQLObj();
        tableObj.setTableAlias("");
        List<DatasetTableFieldDTO> tmp = permissionManage
                .filterColumnPermissions(fields, desensitizationList, id, userId)
                .stream()
                .filter(ele -> {
                    boolean flag = true;
                    if (Objects.equals(ele.getExtField(), ExtFieldConstant.EXT_CALC)) {
                        String originField = Utils.calcFieldRegex(ele, tableObj, fields, true, null, Utils.mergeParam(Utils.getParams(fields), null), pluginManage);
                        for (String func : FunctionConstant.AGG_FUNC) {
                            if (Utils.matchFunction(func, originField)) {
                                flag = false;
                                break;
                            }
                        }
                    }
                    return flag;
                })
                .sorted(Comparator.comparing(DatasetTableFieldDTO::getGroupType))
                .toList();
        tmp.forEach(ele -> ele.setDesensitized(desensitizationList.containsKey(ele.getEngineFieldName())));
        DatasetUtils.listEncode(tmp);
        return tmp;
    }

    /**
     * 将字段记录转换为字段 DTO，并还原 JSON 结构字段
     */
    public DatasetTableFieldDTO transObj(CoreDatasetTableField ele) {
        DatasetTableFieldDTO dto = new DatasetTableFieldDTO();
        if (ele == null) return null;
        BeanUtils.copyBean(dto, ele);
        if (StringUtils.isNotEmpty(ele.getParams())) {
            TypeReference<List<CalParam>> tokenType = new TypeReference<>() {
            };
            List<CalParam> calParams = JsonUtil.parseList(ele.getParams(), tokenType);
            dto.setParams(calParams);
        }
        if (StringUtils.isNotEmpty(ele.getGroupList())) {
            TypeReference<List<FieldGroupDTO>> groupTokenType = new TypeReference<>() {
            };
            List<FieldGroupDTO> fieldGroups = JsonUtil.parseList(ele.getGroupList(), groupTokenType);
            dto.setGroupList(fieldGroups);
        }
        return dto;
    }

    /**
     * 批量将字段记录列表转换为 DTO 列表
     */
    public List<DatasetTableFieldDTO> transDTO(List<CoreDatasetTableField> list) {
        if (!CollectionUtils.isEmpty(list)) {
            return list.stream().map(this::transObj).collect(Collectors.toList());
        } else {
            return new ArrayList<>();
        }
    }

    /**
     * 将字段 DTO 转换为数据库记录，并序列化复杂字段
     */
    private CoreDatasetTableField transDTO2Record(DatasetTableFieldDTO dto) {
        CoreDatasetTableField record = new CoreDatasetTableField();
        BeanUtils.copyBean(record, dto);
        if (ObjectUtils.isNotEmpty(dto.getParams())) {
            record.setParams(JsonUtil.toJSONString(dto.getParams()).toString());
        }
        if (ObjectUtils.isNotEmpty(dto.getGroupList())) {
            record.setGroupList(JsonUtil.toJSONString(dto.getGroupList()).toString());
        }
        return record;
    }

    /**
     * 校验字段名称长度不超过数据库约束
     */
    private void checkNameLength(String name) {
        if (name != null && name.length() > 100) {
            CrestException.throwException(Translator.get("i18n_field_name_limit_100"));
        }
    }

    /**
     * 判断当前数据源类型是否支持 Copilot 字段能力
     */
    public boolean isCopilotSupport(Map<Long, DatasourceSchemaDTO> dsMap) {
        DatasourceSchemaDTO value = dsMap.entrySet().iterator().next().getValue();
        return Strings.CI.equalsAny(value.getType(), "mysql", "obMysql");
    }
}
