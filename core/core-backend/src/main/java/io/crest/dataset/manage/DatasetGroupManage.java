package io.crest.dataset.manage;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import io.crest.api.dataset.union.DatasetGroupInfoDTO;
import io.crest.api.dataset.union.UnionDTO;
import io.crest.api.dataset.vo.DataSetBarVO;
import io.crest.api.permissions.relation.api.RelationApi;
import io.crest.commons.constants.OptConstants;
import io.crest.dataset.dao.auto.entity.CoreDatasetGroup;
import io.crest.dataset.dao.auto.entity.CoreDatasetTable;
import io.crest.dataset.dao.auto.mapper.CoreDatasetGroupMapper;
import io.crest.dataset.dao.auto.mapper.CoreDatasetTableMapper;
import io.crest.dataset.dao.ext.mapper.CoreDataSetExtMapper;
import io.crest.dataset.dao.ext.po.DataSetNodePO;
import io.crest.dataset.dto.DataSetNodeBO;
import io.crest.dataset.sync.DatasetSyncTaskManage;
import io.crest.dataset.utils.DatasetParameterFieldId;
import io.crest.dataset.utils.DatasetUtils;
import io.crest.dataset.utils.FieldUtils;
import io.crest.dataset.utils.TableUtils;
import io.crest.datasource.dao.auto.entity.CoreDatasource;
import io.crest.datasource.dao.auto.mapper.CoreDatasourceMapper;
import io.crest.engine.constant.ExtFieldConstant;
import io.crest.engine.func.FunctionConstant;
import io.crest.engine.utils.Utils;
import io.crest.exception.CrestException;
import io.crest.extensions.datasource.api.PluginManageApi;
import io.crest.extensions.datasource.dto.DatasetTableDTO;
import io.crest.extensions.datasource.dto.DatasetTableFieldDTO;
import io.crest.extensions.datasource.dto.DatasourceDTO;
import io.crest.extensions.datasource.model.SQLObj;
import io.crest.extensions.view.dto.SqlVariableDetails;
import io.crest.i18n.Translator;
import io.crest.model.BusiNodeRequest;
import io.crest.model.BusiNodeVO;
import io.crest.operation.manage.CoreOptRecentManage;
import io.crest.portal.DataPortalPermissionManage;
import io.crest.substitute.permissions.auth.PlatformPermissionManage;
import io.crest.system.manage.CoreUserManage;
import io.crest.utils.*;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 数据集目录与数据集定义的领域服务，负责节点保存、移动删除、权限校验和详情装配
 * 该类只维护数据集元数据及其表字段关系，具体 SQL 生成和数据预览由下游管理器完成
 */
@Component
@Transactional(rollbackFor = Exception.class)
@SuppressWarnings("unchecked")
public class DatasetGroupManage {
    @Resource
    private CoreDatasetGroupMapper coreDatasetGroupMapper;
    @Resource
    private DatasetSQLManage datasetSQLManage;
    @Resource
    private DatasetDataManage datasetDataManage;
    @Resource
    private DatasetTableManage datasetTableManage;
    @Resource
    private DatasetTableFieldManage datasetTableFieldManage;
    @Resource
    private PermissionManage permissionManage;
    @Resource
    private CoreDataSetExtMapper coreDataSetExtMapper;
    @Resource
    private CoreDatasetTableMapper coreDatasetTableMapper;
    @Resource
    private CoreDatasourceMapper coreDatasourceMapper;
    @Resource
    private DatasetSyncTaskManage datasetSyncTaskManage;


    @Resource
    private CoreUserManage coreUserManage;

    @Resource
    private CoreOptRecentManage coreOptRecentManage;

    @Autowired(required = false)
    private RelationApi relationManage;
    @Autowired(required = false)
    private PluginManageApi pluginManage;

    @Resource
    private PlatformPermissionManage platformPermissionManage;

    @Resource
    private DataPortalPermissionManage dataPortalPermissionManage;

    private static final String leafType = "dataset";

    private Lock lock = new ReentrantLock();


    /**
     * 保存数据集或目录节点，并在数据集节点保存时同步联合树、字段定义和 SQL 快照
     */
    @Transactional
    public DatasetGroupInfoDTO save(DatasetGroupInfoDTO datasetGroupInfoDTO, boolean rename, boolean encode) throws Exception {
        try {
            boolean isCreate;
            // 重命名请求可能只带节点 ID，需要回填父节点以复用名称校验流程
            if (ObjectUtils.isEmpty(datasetGroupInfoDTO.getPid()) && ObjectUtils.isNotEmpty(datasetGroupInfoDTO.getId())) {
                requireDatasetAccess(datasetGroupInfoDTO.getId());
                CoreDatasetGroup coreDatasetGroup = coreDatasetGroupMapper.selectById(datasetGroupInfoDTO.getId());
                datasetGroupInfoDTO.setPid(coreDatasetGroup.getPid());
            }
            if (ObjectUtils.isNotEmpty(datasetGroupInfoDTO.getId())) {
                requireDatasetAccess(datasetGroupInfoDTO.getId());
            }
            if (ObjectUtils.isNotEmpty(datasetGroupInfoDTO.getPid()) && !Objects.equals(datasetGroupInfoDTO.getPid(), 0L)) {
                requireDatasetAccess(datasetGroupInfoDTO.getPid());
            }
            datasetGroupInfoDTO.setUpdateBy(AuthUtils.getUser().getUserId() + "");
            datasetGroupInfoDTO.setLastUpdateTime(System.currentTimeMillis());
            if (Strings.CI.equals(datasetGroupInfoDTO.getNodeType(), leafType)) {
                if (!rename && ObjectUtils.isEmpty(datasetGroupInfoDTO.getAllFields())) {
                    CrestException.throwException(Translator.get("i18n_no_fields"));
                }
                // 数据集节点保存前先生成联合 SQL，保证持久化的结构和查询文本一致
                Map<String, Object> sqlMap = datasetSQLManage.getUnionSQLForEdit(datasetGroupInfoDTO, null);
                if (ObjectUtils.isNotEmpty(sqlMap)) {
                    String sql = (String) sqlMap.get("sql");
                    datasetGroupInfoDTO.setUnionSql(sql);
                    datasetGroupInfoDTO.setInfo(Objects.requireNonNull(JsonUtil.toJSONString(datasetGroupInfoDTO.getUnion())).toString());
                }
            }
            // 新建和编辑分别走代理方法，确保事务与平台权限资源同步在同一边界内完成
            long time = System.currentTimeMillis();
            if (ObjectUtils.isEmpty(datasetGroupInfoDTO.getId())) {
                isCreate = true;
                datasetGroupInfoDTO.setId(IDUtils.snowID());
                datasetGroupInfoDTO.setCreateBy(AuthUtils.getUser().getUserId() + "");
                datasetGroupInfoDTO.setUpdateBy(AuthUtils.getUser().getUserId() + "");
                datasetGroupInfoDTO.setCreateTime(time);
                datasetGroupInfoDTO.setLastUpdateTime(time);
                datasetGroupInfoDTO.setPid(datasetGroupInfoDTO.getPid() == null ? 0L : datasetGroupInfoDTO.getPid());
                Objects.requireNonNull(CommonBeanFactory.getBean(this.getClass())).innerSave(datasetGroupInfoDTO);
            } else {
                isCreate = false;
                if (Objects.equals(datasetGroupInfoDTO.getId(), datasetGroupInfoDTO.getPid())) {
                    CrestException.throwException(Translator.get("i18n_pid_not_eq_id"));
                }
                Objects.requireNonNull(CommonBeanFactory.getBean(this.getClass())).innerEdit(datasetGroupInfoDTO);
            }
            // 数据集节点需要同步数据表和字段定义。
            if (Strings.CI.equals(datasetGroupInfoDTO.getNodeType(), "dataset")) {
                if (encode) {
                    DatasetUtils.dsDecode(datasetGroupInfoDTO);
                }
                List<Long> tableIds = new ArrayList<>();
                List<Long> fieldIds = new ArrayList<>();
                // 解析数据集树结构并保存表字段关系。
                saveTable(datasetGroupInfoDTO, datasetGroupInfoDTO.getUnion(), tableIds, isCreate);
                saveField(datasetGroupInfoDTO, fieldIds);
                // 删除本次编辑后不再引用的数据表和字段。
                datasetTableManage.deleteByDatasetGroupUpdate(datasetGroupInfoDTO.getId(), tableIds);
                datasetTableFieldManage.deleteByDatasetGroupUpdate(datasetGroupInfoDTO.getId(), fieldIds);
                if (encode) {
                    DatasetUtils.dsEncode(datasetGroupInfoDTO);
                }
            }
            if (StringUtils.isNotEmpty(datasetGroupInfoDTO.getUnionSql())) {
                datasetGroupInfoDTO.setUnionSql(DatasetUtils.getEncode(datasetGroupInfoDTO.getUnionSql()));
            }
            return datasetGroupInfoDTO;
        } catch (Exception e) {
            CrestException.throwException(e.getMessage());
        }
        return null;
    }

    /**
     * 编辑已有数据集或目录节点，并同步平台资源索引和最近操作记录
     */
    public void innerEdit(DatasetGroupInfoDTO datasetGroupInfoDTO) {
        requireDatasetAccess(datasetGroupInfoDTO.getId());
        checkName(datasetGroupInfoDTO);
        CoreDatasetGroup coreDatasetGroup = BeanUtils.copyBean(new CoreDatasetGroup(), datasetGroupInfoDTO);
        coreDatasetGroup.setLastUpdateTime(System.currentTimeMillis());
        coreDatasetGroupMapper.updateById(coreDatasetGroup);
        platformPermissionManage.upsertResource("dataset", String.valueOf(coreDatasetGroup.getId()),
                AuthUtils.getUser().getDefaultOid(), AuthUtils.getUser().getUserId(), coreDatasetGroup.getName(),
                coreDatasetGroup.getCreateTime(), coreDatasetGroup.getLastUpdateTime());
        coreOptRecentManage.saveOpt(datasetGroupInfoDTO.getId(), OptConstants.OPT_RESOURCE_TYPE.DATASET, OptConstants.OPT_TYPE.UPDATE);
    }

    /**
     * 新建数据集或目录节点，并在数据集节点创建后写入平台资源索引
     */
    public void innerSave(DatasetGroupInfoDTO datasetGroupInfoDTO) {
        checkName(datasetGroupInfoDTO);
        CoreDatasetGroup coreDatasetGroup = BeanUtils.copyBean(new CoreDatasetGroup(), datasetGroupInfoDTO);
        coreDatasetGroupMapper.insert(coreDatasetGroup);
        if (Strings.CI.equals(coreDatasetGroup.getNodeType(), "dataset")) {
            platformPermissionManage.upsertResource("dataset", String.valueOf(coreDatasetGroup.getId()),
                    AuthUtils.getUser().getDefaultOid(), AuthUtils.getUser().getUserId(), coreDatasetGroup.getName(),
                    coreDatasetGroup.getCreateTime(), coreDatasetGroup.getLastUpdateTime());
        }
        coreOptRecentManage.saveOpt(coreDatasetGroup.getId(), OptConstants.OPT_RESOURCE_TYPE.DATASET, OptConstants.OPT_TYPE.NEW);
    }

    /**
     * 移动数据集或目录节点，校验目标父节点权限和循环引用后更新父子关系
     */
    public DatasetGroupInfoDTO move(DatasetGroupInfoDTO datasetGroupInfoDTO) {
        requireDatasetAccess(datasetGroupInfoDTO.getId());
        if (ObjectUtils.isNotEmpty(datasetGroupInfoDTO.getPid()) && !Objects.equals(datasetGroupInfoDTO.getPid(), 0L)) {
            requireDatasetAccess(datasetGroupInfoDTO.getPid());
        }
        checkName(datasetGroupInfoDTO);
        if (datasetGroupInfoDTO.getPid() != 0) {
            checkMove(datasetGroupInfoDTO);
        }
        // 仅更新节点位置和展示信息，不重建数据集表字段关系
        long time = System.currentTimeMillis();
        CoreDatasetGroup coreDatasetGroup = new CoreDatasetGroup();
        BeanUtils.copyBean(coreDatasetGroup, datasetGroupInfoDTO);
        datasetGroupInfoDTO.setUpdateBy(AuthUtils.getUser().getUserId() + "");
        coreDatasetGroup.setLastUpdateTime(time);
        coreDatasetGroupMapper.updateById(coreDatasetGroup);
        platformPermissionManage.upsertResource("dataset", String.valueOf(coreDatasetGroup.getId()),
                AuthUtils.getUser().getDefaultOid(), AuthUtils.getUser().getUserId(), coreDatasetGroup.getName(),
                coreDatasetGroup.getCreateTime(), coreDatasetGroup.getLastUpdateTime());
        coreOptRecentManage.saveOpt(coreDatasetGroup.getId(), OptConstants.OPT_RESOURCE_TYPE.DATASET, OptConstants.OPT_TYPE.UPDATE);
        return datasetGroupInfoDTO;
    }

    /**
     * 判断数据集是否仍被关系模块引用，用于删除前的业务占用校验
     */
    public boolean perDelete(Long id) {
        if (relationManage != null) {
            Long count = relationManage.datasetResource(id);
            if (count > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 删除数据集或目录树，并先校验整棵子树的访问权限
     */
    public void delete(Long id) {
        CoreDatasetGroup coreDatasetGroup = coreDatasetGroupMapper.selectById(id);
        if (ObjectUtils.isEmpty(coreDatasetGroup)) {
            CrestException.throwException("resource not exist");
        }
        requireDatasetTreeAccess(id);
        Objects.requireNonNull(CommonBeanFactory.getBean(this.getClass())).recursionDel(id);
        coreOptRecentManage.saveOpt(coreDatasetGroup.getId(), OptConstants.OPT_RESOURCE_TYPE.DATASET, OptConstants.OPT_TYPE.DELETE);
    }

    /**
     * 递归删除节点、同步任务、表字段关系和所有子节点
     */
    public void recursionDel(Long id) {
        datasetSyncTaskManage.deleteByDatasetGroupId(id);
        coreDatasetGroupMapper.deleteById(id);
        datasetTableManage.deleteByDatasetGroupDelete(id);
        datasetTableFieldManage.deleteByDatasetGroupDelete(id);

        QueryWrapper<CoreDatasetGroup> wrapper = new QueryWrapper<>();
        wrapper.eq("pid", id);
        List<CoreDatasetGroup> coreDatasetGroups = coreDatasetGroupMapper.selectList(wrapper);
        if (ObjectUtils.isNotEmpty(coreDatasetGroups)) {
            for (CoreDatasetGroup record : coreDatasetGroups) {
                recursionDel(record.getId());
            }
        }
    }

    /**
     * 查询当前用户可见的数据集树，按叶子类型和平台资源权限裁剪结果
     */
    public List<BusiNodeVO> tree(BusiNodeRequest request) {

        QueryWrapper<Object> queryWrapper = new QueryWrapper<>();
        if (ObjectUtils.isNotEmpty(request.getLeaf())) {
            queryWrapper.eq("node_type", request.getLeaf() ? "dataset" : "folder");
        }
        String info = CommunityUtils.getInfo();
        if (StringUtils.isNotBlank(info)) {
            queryWrapper.notExists(String.format(info, "core_dataset.id"));
        }
        String scopeSql = platformPermissionManage.resourceScopeSql("dataset", "core_dataset.id", "core_dataset.create_by", null);
        if (StringUtils.isNotBlank(scopeSql)) {
            queryWrapper.apply(scopeSql);
        }
        queryWrapper.orderByDesc("create_time");
        List<DataSetNodePO> pos = coreDataSetExtMapper.query(queryWrapper);
        List<DataSetNodeBO> nodes = new ArrayList<>();
        if (ObjectUtils.isEmpty(request.getLeaf()) || !request.getLeaf()) nodes.add(rootNode());
        List<DataSetNodeBO> bos = pos.stream().map(this::convert).toList();
        if (CollectionUtils.isNotEmpty(bos)) {
            nodes.addAll(bos);
        }
        return TreeUtils.mergeTree(nodes, BusiNodeVO.class, false);
    }

    /**
     * 查询数据集顶部信息，包括创建人、更新人和关联数据源摘要
     */
    public DataSetBarVO queryBarInfo(Long id) {
        requireDatasetAccess(id);
        DataSetBarVO dataSetBarVO = coreDataSetExtMapper.queryBarInfo(id);
        // 创建人和更新人展示名只作为视图信息返回，不参与权限判断
        String userName = coreUserManage.getUserName(Long.valueOf(dataSetBarVO.getCreateBy()));
        if (StringUtils.isNotBlank(userName)) {
            dataSetBarVO.setCreator(userName);
        }
        String updateUserName = coreUserManage.getUserName(Long.valueOf(dataSetBarVO.getUpdateBy()));
        if (StringUtils.isNotBlank(updateUserName)) {
            dataSetBarVO.setUpdater(updateUserName);
        }
        dataSetBarVO.setDatasourceDTOList(datasource(id));
        return dataSetBarVO;
    }

    /**
     * 查询数据集引用的数据源列表，并隐藏连接配置等敏感字段
     */
    private List<DatasourceDTO> datasource(Long datasetId) {
        requireDatasetAccess(datasetId);
        QueryWrapper<CoreDatasetTable> wrapper = new QueryWrapper<>();
        wrapper.eq("dataset_group_id", datasetId);
        List<CoreDatasetTable> coreDatasetTables = coreDatasetTableMapper.selectList(wrapper);
        Set<Long> ids = new LinkedHashSet();
        coreDatasetTables.forEach(ele -> ids.add(ele.getDatasourceId()));
        if (CollectionUtils.isEmpty(ids)) {
            CrestException.throwException(Translator.get("i18n_dataset_create_error"));
        }

        QueryWrapper<CoreDatasource> datasourceQueryWrapper = new QueryWrapper<>();
        datasourceQueryWrapper.in("id", ids);
        List<DatasourceDTO> datasourceDTOList = coreDatasourceMapper.selectList(datasourceQueryWrapper).stream().map(ele -> {
            DatasourceDTO dto = new DatasourceDTO();
            BeanUtils.copyBean(dto, ele);
            dto.setConfiguration(null);
            return dto;
        }).collect(Collectors.toList());
        if (ids.size() != datasourceDTOList.size()) {
            CrestException.throwException(Translator.get("i18n_dataset_ds_delete"));
        }
        return datasourceDTOList;
    }

    /**
     * 构造数据集树的虚拟根节点
     */
    private DataSetNodeBO rootNode() {
        return new DataSetNodeBO(0L, "root", false, 7, -1L, 0);
    }

    /**
     * 将数据库查询节点转换为前端树节点需要的业务对象
     */
    private DataSetNodeBO convert(DataSetNodePO po) {
        return new DataSetNodeBO(po.getId(), po.getName(), Strings.CS.equals(po.getNodeType(), leafType), 9, po.getPid(), 0);
    }

    /**
     * 预留名称校验入口，当前名称冲突规则由上游或数据库约束承担
     */
    public void checkName(DatasetGroupInfoDTO dto) {
    }

    /**
     * 递归保存联合树中的数据表节点，并收集本次仍然有效的表 ID
     */
    public void saveTable(DatasetGroupInfoDTO datasetGroupInfoDTO, List<UnionDTO> union, List<Long> tableIds, boolean isCreate) {
        // 表和字段 ID 由前端在缺省时生成，后端在这里负责补齐所属数据集关系
        Long datasetGroupId = datasetGroupInfoDTO.getId();
        if (ObjectUtils.isNotEmpty(union)) {
            for (UnionDTO unionDTO : union) {
                DatasetTableDTO currentDs = unionDTO.getCurrentDs();
                if (ObjectUtils.isNotEmpty(currentDs.getDatasourceId())) {
                    requireDatasourceAccess(currentDs.getDatasourceId());
                }
                CoreDatasetTable coreDatasetTable = datasetTableManage.selectById(currentDs.getId());
                if (coreDatasetTable != null && isCreate) {
                    CrestException.throwException(Translator.get("i18n_table_duplicate"));
                }
                currentDs.setDatasetGroupId(datasetGroupId);
                datasetTableManage.save(currentDs);
                tableIds.add(currentDs.getId());

                saveTable(datasetGroupInfoDTO, unionDTO.getChildrenDs(), tableIds, isCreate);
            }
        }
    }

    /**
     * 保存数据集字段定义，保持普通字段、计算字段和分组字段的引擎字段名稳定
     */
    public void saveField(DatasetGroupInfoDTO datasetGroupInfoDTO, List<Long> fieldIds) throws Exception {
        if (ObjectUtils.isEmpty(datasetGroupInfoDTO.getUnion())) {
            return;
        }
        datasetDataManage.previewDataWithLimit(datasetGroupInfoDTO, 0, 1, false, false);
        // 保存字段前先预览一行数据，用于校验联合 SQL 和字段映射可正常生成
        Long datasetGroupId = datasetGroupInfoDTO.getId();
        List<DatasetTableFieldDTO> allFields = datasetGroupInfoDTO.getAllFields();
        if (ObjectUtils.isNotEmpty(allFields)) {
            // 联合 SQL 生成结果提供普通字段的引擎字段名，避免重复编辑后别名漂移
            Map<String, Object> map = datasetSQLManage.getUnionSQLForEdit(datasetGroupInfoDTO, null);
            List<DatasetTableFieldDTO> unionFields = (List<DatasetTableFieldDTO>) map.get("field");

            for (DatasetTableFieldDTO datasetTableFieldDTO : allFields) {
                DatasetTableFieldDTO dto = datasetTableFieldManage.selectById(datasetTableFieldDTO.getId());
                if (ObjectUtils.isEmpty(dto)) {
                    if (Objects.equals(datasetTableFieldDTO.getExtField(), ExtFieldConstant.EXT_NORMAL)) {
                        for (DatasetTableFieldDTO fieldDTO : unionFields) {
                            if (Objects.equals(datasetTableFieldDTO.getDatasetTableId(), fieldDTO.getDatasetTableId())
                                    && Objects.equals(datasetTableFieldDTO.getOriginName(), fieldDTO.getOriginName())) {
                                datasetTableFieldDTO.setEngineFieldName(fieldDTO.getEngineFieldName());
                                datasetTableFieldDTO.setFieldShortName(fieldDTO.getFieldShortName());
                            }
                        }
                    }
                    if (Objects.equals(datasetTableFieldDTO.getExtField(), ExtFieldConstant.EXT_CALC)) {
                        String engineFieldName = TableUtils.fieldNameShort(datasetTableFieldDTO.getId() + "_" + datasetTableFieldDTO.getOriginName());
                        datasetTableFieldDTO.setEngineFieldName(engineFieldName);
                        datasetTableFieldDTO.setFieldShortName(engineFieldName);
                        datasetTableFieldDTO.setExtractedFieldType(datasetTableFieldDTO.getFieldType());
                    }
                    if (Objects.equals(datasetTableFieldDTO.getExtField(), ExtFieldConstant.EXT_GROUP)) {
                        String engineFieldName = TableUtils.fieldNameShort(datasetTableFieldDTO.getId() + "_" + datasetTableFieldDTO.getOriginName());
                        datasetTableFieldDTO.setEngineFieldName(engineFieldName);
                        datasetTableFieldDTO.setFieldShortName(engineFieldName);
                        datasetTableFieldDTO.setExtractedFieldType(0);
                        datasetTableFieldDTO.setFieldType(0);
                        datasetTableFieldDTO.setGroupType("d");
                    }
                    datasetTableFieldDTO.setDatasetGroupId(datasetGroupId);
                } else {
                    datasetTableFieldDTO.setEngineFieldName(dto.getEngineFieldName());
                    datasetTableFieldDTO.setFieldShortName(dto.getFieldShortName());
                }
                datasetTableFieldDTO = datasetTableFieldManage.save(datasetTableFieldDTO);
                fieldIds.add(datasetTableFieldDTO.getId());
            }
        }
    }

    /**
     * 查询计数场景所需的数据集定义，只返回字段结构，不组装预览数据
     */
    public DatasetGroupInfoDTO getForCount(Long id) throws Exception {
        requireDatasetAccess(id);
        CoreDatasetGroup coreDatasetGroup = coreDatasetGroupMapper.selectById(id);
        if (coreDatasetGroup == null) {
            return null;
        }
        DatasetGroupInfoDTO dto = new DatasetGroupInfoDTO();
        BeanUtils.copyBean(dto, coreDatasetGroup);
        if (Strings.CI.equals(dto.getNodeType(), "dataset")) {
            dto.setUnion(JsonUtil.parseList(coreDatasetGroup.getInfo(), new TypeReference<>() {
            }));
            normalizeDatasetCrossFlag(dto);
            // 字段短名在计数场景复用引擎字段名，方便后续 SQL 聚合引用
            List<DatasetTableFieldDTO> dsFields = datasetTableFieldManage.selectByDatasetGroupId(id);
            List<DatasetTableFieldDTO> allFields = dsFields.stream().map(ele -> {
                DatasetTableFieldDTO datasetTableFieldDTO = new DatasetTableFieldDTO();
                BeanUtils.copyBean(datasetTableFieldDTO, ele);
                datasetTableFieldDTO.setFieldShortName(ele.getEngineFieldName());
                return datasetTableFieldDTO;
            }).collect(Collectors.toList());

            dto.setAllFields(allFields);
        }
        return dto;
    }

    /**
     * 查询数据集详情，返回联合树、字段配置和展示所需的创建更新人信息
     */
    public DatasetGroupInfoDTO getDetail(Long id) throws Exception {
        requireDatasetAccess(id);
        CoreDatasetGroup coreDatasetGroup = coreDatasetGroupMapper.selectById(id);
        if (coreDatasetGroup == null) {
            return null;
        }
        DatasetGroupInfoDTO dto = new DatasetGroupInfoDTO();
        BeanUtils.copyBean(dto, coreDatasetGroup);
        // 展示用人员名称单独查询，避免把用户表结构暴露给调用方
        String userName = coreUserManage.getUserName(Long.valueOf(dto.getCreateBy()));
        if (StringUtils.isNotBlank(userName)) {
            dto.setCreator(userName);
        }
        String updateUserName = coreUserManage.getUserName(Long.valueOf(dto.getUpdateBy()));
        if (StringUtils.isNotBlank(updateUserName)) {
            dto.setUpdater(updateUserName);
        }
        dto.setUnionSql(null);
        if (Strings.CI.equals(dto.getNodeType(), "dataset")) {
            List<UnionDTO> unionDTOList = JsonUtil.parseList(coreDatasetGroup.getInfo(), new TypeReference<>() {
            });
            dto.setUnion(unionDTOList);
            normalizeDatasetCrossFlag(dto);

            // 字段返回前需要编码，保持和前端字段配置协议一致
            List<DatasetTableFieldDTO> dsFields = datasetTableFieldManage.selectByDatasetGroupId(id);
            List<DatasetTableFieldDTO> allFields = dsFields.stream().map(ele -> {
                DatasetTableFieldDTO datasetTableFieldDTO = new DatasetTableFieldDTO();
                BeanUtils.copyBean(datasetTableFieldDTO, ele);
                datasetTableFieldDTO.setFieldShortName(ele.getEngineFieldName());
                return datasetTableFieldDTO;
            }).collect(Collectors.toList());

            DatasetUtils.listEncode(allFields);

            dto.setAllFields(allFields);
        }
        normalizeDatasetCrossFlag(dto);
        return dto;
    }

    /**
     * 查询数据集基础信息，默认执行数据集访问权限校验
     */
    public DatasetGroupInfoDTO datasetGroupInfoDTO(Long id, String type) throws Exception {
        return datasetGroupInfoDTO(id, type, true);
    }

    /**
     * 查询已发布可视化读取数据集时使用的基础信息，可在门户授权命中时跳过数据集所有者校验
     */
    public DatasetGroupInfoDTO datasetGroupInfoDTOForVisualizationRead(Long id, String type, Long visualizationId) throws Exception {
        boolean skipDatasetAccess = visualizationId != null
                && dataPortalPermissionManage.canReadPublishedVisualizationDataset(visualizationId, id);
        return datasetGroupInfoDTO(id, type, !skipDatasetAccess);
    }

    /**
     * 按用途查询数据集信息，预览场景会附带样例数据、SQL 和总数
     */
    private DatasetGroupInfoDTO datasetGroupInfoDTO(Long id, String type, boolean checkAccess) throws Exception {
        if (checkAccess) {
            requireDatasetAccess(id);
        }
        CoreDatasetGroup coreDatasetGroup = coreDatasetGroupMapper.selectById(id);
        if (coreDatasetGroup == null) {
            return null;
        }
        DatasetGroupInfoDTO dto = new DatasetGroupInfoDTO();
        BeanUtils.copyBean(dto, coreDatasetGroup);
        // 创建人和更新人名称仅用于前端展示
        String userName = coreUserManage.getUserName(Long.valueOf(dto.getCreateBy()));
        if (StringUtils.isNotBlank(userName)) {
            dto.setCreator(userName);
        }
        String updateUserName = coreUserManage.getUserName(Long.valueOf(dto.getUpdateBy()));
        if (StringUtils.isNotBlank(updateUserName)) {
            dto.setUpdater(updateUserName);
        }
        dto.setUnionSql(null);
        if (Strings.CI.equals(dto.getNodeType(), "dataset")) {
            List<UnionDTO> unionDTOList = JsonUtil.parseList(coreDatasetGroup.getInfo(), new TypeReference<>() {
            });
            dto.setUnion(unionDTOList);
            normalizeDatasetCrossFlag(dto);

            // 字段结构与联合树一起返回，供编辑器恢复数据集配置
            List<DatasetTableFieldDTO> dsFields = datasetTableFieldManage.selectByDatasetGroupId(id);
            List<DatasetTableFieldDTO> allFields = dsFields.stream().map(ele -> {
                DatasetTableFieldDTO datasetTableFieldDTO = new DatasetTableFieldDTO();
                BeanUtils.copyBean(datasetTableFieldDTO, ele);
                datasetTableFieldDTO.setFieldShortName(ele.getEngineFieldName());
                return datasetTableFieldDTO;
            }).collect(Collectors.toList());

            dto.setAllFields(allFields);

            if ("preview".equalsIgnoreCase(type)) {
                // 预览模式只读取前 100 行，避免详情接口承担完整数据查询压力
                Map<String, Object> map = datasetDataManage.previewDataWithLimit(dto, 0, 100, true, false);
                // SQL 以 Base64 返回，避免原始 SQL 中的特殊字符影响传输协议
                Map<String, List> data = (Map<String, List>) map.get("data");
                String sql = (String) map.get("sql");
                Long total = (Long) map.get("total");
                dto.setData(data);
                dto.setSql(Base64.getEncoder().encodeToString(sql.getBytes()));
                dto.setTotal(total);
            }
        }
        return dto;
    }

    /**
     * 批量查询数据集对应的数据表字段结构，按输入顺序返回结果
     */
    public List<DatasetTableDTO> getDetail(List<Long> ids) {
        if (ObjectUtils.isEmpty(ids)) {
            CrestException.throwException(Translator.get("i18n_table_id_can_not_empty"));
        }
        List<DatasetTableDTO> list = new ArrayList<>();
        for (Long id : ids) {
            requireDatasetAccess(id);
            CoreDatasetGroup coreDatasetGroup = coreDatasetGroupMapper.selectById(id);
            if (coreDatasetGroup == null) {
                list.add(null);
            } else {
                DatasetTableDTO dto = new DatasetTableDTO();
                BeanUtils.copyBean(dto, coreDatasetGroup);
                dto.setIsCross(resolveDatasetCrossFlag(coreDatasetGroup));
                Map<String, List<DatasetTableFieldDTO>> listByDQ = datasetTableFieldManage.listByDQ(id);
                dto.setFields(listByDQ);
                list.add(dto);
            }
        }
        return list;
    }

    /**
     * 查询多个数据集自定义 SQL 中声明的参数，并执行数据集访问权限校验
     */
    public List<SqlVariableDetails> sqlParams(List<Long> ids) {
        return sqlParams(ids, true);
    }

    /**
     * 查询已发布可视化可读取数据集的 SQL 参数，门户授权命中时跳过所有者校验
     */
    public List<SqlVariableDetails> sqlParamsForVisualizationRead(List<Long> ids, Long visualizationId) {
        boolean skipDatasetAccess = visualizationId != null
                && ObjectUtils.isNotEmpty(ids)
                && ids.stream().allMatch(id -> dataPortalPermissionManage
                .canReadPublishedVisualizationDataset(visualizationId, id));
        return sqlParams(ids, !skipDatasetAccess);
    }

    /**
     * 聚合数据集表中的 SQL 参数，并补齐数据集路径、字段类型和前端唯一标识
     */
    private List<SqlVariableDetails> sqlParams(List<Long> ids, boolean checkAccess) {
        List<SqlVariableDetails> list = new ArrayList<>();
        if (ObjectUtils.isEmpty(ids)) {
            return list;
        }
        TypeReference<List<SqlVariableDetails>> listTypeReference = new TypeReference<List<SqlVariableDetails>>() {
        };
        for (Long id : ids) {
            if (checkAccess) {
                requireDatasetAccess(id);
            }
            List<CoreDatasetTable> datasetTables = datasetTableManage.selectByDatasetGroupId(id);
            for (CoreDatasetTable datasetTable : datasetTables) {
                if (StringUtils.isNotEmpty(datasetTable.getSqlVariableDetails())) {
                    List<SqlVariableDetails> defaultsSqlVariableDetails = JsonUtil.parseList(datasetTable.getSqlVariableDetails(), listTypeReference);
                    if (CollectionUtils.isNotEmpty(defaultsSqlVariableDetails)) {
                        List<String> fullName = new ArrayList<>();
                        geFullName(id, fullName);
                        Collections.reverse(fullName);
                        List<String> finalFullName = fullName;
                        defaultsSqlVariableDetails.forEach(sqlVariableDetails -> {
                            sqlVariableDetails.setDatasetGroupId(id);
                            sqlVariableDetails.setDatasetTableId(datasetTable.getId());
                            sqlVariableDetails.setDatasetFullName(String.join("/", finalFullName));
                        });
                    }

                    list.addAll(defaultsSqlVariableDetails);
                }
            }
        }
        list.forEach(sqlVariableDetail -> {
            sqlVariableDetail.setId(DatasetParameterFieldId.build(sqlVariableDetail.getDatasetTableId(), sqlVariableDetail.getVariableName()));
            sqlVariableDetail.setFieldType(FieldUtils.resolveFieldType(sqlVariableDetail.getType().get(0).contains("DATETIME") ? "DATETIME" : sqlVariableDetail.getType().get(0)));
        });
        return list;
    }

    /**
     * 校验移动目标不会把节点放到自己或自己的子孙节点下
     */
    public void checkMove(DatasetGroupInfoDTO datasetGroupInfoDTO) {
        if (Objects.equals(datasetGroupInfoDTO.getId(), datasetGroupInfoDTO.getPid())) {
            CrestException.throwException(Translator.get("i18n_pid_not_eq_id"));
        }
        List<Long> ids = new ArrayList<>();
        getParents(datasetGroupInfoDTO.getPid(), ids);
        if (ids.contains(datasetGroupInfoDTO.getId())) {
            CrestException.throwException(Translator.get("i18n_pid_not_eq_id"));
        }
    }

    /**
     * 递归收集指定父节点到根节点之间的祖先 ID
     */
    private void getParents(Long pid, List<Long> ids) {
        // 查询父级目录并继续向根节点回溯
        CoreDatasetGroup parent = coreDatasetGroupMapper.selectById(pid);
        ids.add(parent.getId());
        if (parent.getPid() != null && parent.getPid() != 0) {
            getParents(parent.getPid(), ids);
        }
    }

    /**
     * 递归收集数据集所在目录的完整路径名称
     */
    public void geFullName(Long pid, List<String> fullName) {
        // 查询父级目录并把名称追加到路径片段中
        CoreDatasetGroup parent = coreDatasetGroupMapper.selectById(pid);
        if (parent == null) {
            return;
        }
        fullName.add(parent.getName());
        if (parent.getId().equals(parent.getPid())) {
            return;
        }
        if (parent.getPid() != null && parent.getPid() != 0) {
            geFullName(parent.getPid(), fullName);
        }
    }

    /**
     * 查询带字段权限的数据集详情，并过滤不适合作为维度或指标直接展示的聚合计算字段
     */
    public List<DatasetTableDTO> getDetailWithPerm(List<Long> ids) {
        var result = new ArrayList<DatasetTableDTO>();
        if (CollectionUtils.isNotEmpty(ids)) {
            var dsList = coreDatasetGroupMapper.selectBatchIds(ids);
            if (CollectionUtils.isNotEmpty(dsList)) {
                SQLObj tableObj = new SQLObj();
                tableObj.setTableAlias("");
                dsList.forEach(ds -> {
                    CrestPermissionUtils.requireCreator(ds.getCreateBy());
                    DatasetTableDTO dto = new DatasetTableDTO();
                    BeanUtils.copyBean(dto, ds);
                    var fields = datasetTableFieldManage.listFieldsWithPermissions(ds.getId());
                    var p_fields = fields.stream().filter(ele -> {
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
                    }).toList();
                    List<DatasetTableFieldDTO> dimensionList = p_fields.stream().filter(ele -> Strings.CI.equals(ele.getGroupType(), "d")).toList();
                    List<DatasetTableFieldDTO> quotaList = p_fields.stream().filter(ele -> Strings.CI.equals(ele.getGroupType(), "q")).toList();
                    Map<String, List<DatasetTableFieldDTO>> map = new LinkedHashMap<>();
                    DatasetUtils.listEncode(dimensionList);
                    DatasetUtils.listEncode(quotaList);
                    map.put("dimensionList", dimensionList);
                    map.put("quotaList", quotaList);
                    dto.setFields(map);
                    result.add(dto);
                });
            }
        }
        return result;
    }

    /**
     * 查询当前用户可访问的全部数据集定义，用于批处理或兼容性补齐任务
     */
    public List<DatasetGroupInfoDTO> getAllList() {
        List<CoreDatasetGroup> coreDatasetGroupList = coreDatasetGroupMapper.selectList(new QueryWrapper<>());
        if (CollectionUtils.isEmpty(coreDatasetGroupList)) {
            return new ArrayList<>();
        }
        List<DatasetGroupInfoDTO> list = new ArrayList<>();
        for (CoreDatasetGroup coreDatasetGroup : coreDatasetGroupList) {
            if (!CrestPermissionUtils.canAccessCreator(coreDatasetGroup.getCreateBy())) {
                continue;
            }
            DatasetGroupInfoDTO dto = new DatasetGroupInfoDTO();
            BeanUtils.copyBean(dto, coreDatasetGroup);
            dto.setUnionSql(null);
            if (Strings.CI.equals(dto.getNodeType(), "dataset")) {
                List<UnionDTO> unionDTOList = JsonUtil.parseList(coreDatasetGroup.getInfo(), new TypeReference<>() {
                });
                dto.setUnion(unionDTOList);
                normalizeDatasetCrossFlag(dto);

                // 字段编码后返回，保证与编辑详情接口的字段协议一致
                List<DatasetTableFieldDTO> dsFields = datasetTableFieldManage.selectByDatasetGroupId(coreDatasetGroup.getId());
                List<DatasetTableFieldDTO> allFields = dsFields.stream().map(ele -> {
                    DatasetTableFieldDTO datasetTableFieldDTO = new DatasetTableFieldDTO();
                    BeanUtils.copyBean(datasetTableFieldDTO, ele);
                    datasetTableFieldDTO.setFieldShortName(ele.getEngineFieldName());
                    return datasetTableFieldDTO;
                }).collect(Collectors.toList());

                DatasetUtils.listEncode(allFields);

                dto.setAllFields(allFields);

                list.add(dto);
            }
        }
        return list;
    }

    /**
     * 兼容旧数据和外部导入数据：只有跨源标记为空时才按联合树推导，避免覆盖已经确认的配置。
     */
    private void normalizeDatasetCrossFlag(DatasetGroupInfoDTO dto) {
        if (dto == null || dto.getIsCross() != null) {
            return;
        }
        if (Strings.CI.equals(dto.getNodeType(), leafType) && CollectionUtils.isNotEmpty(dto.getUnion())) {
            datasetSQLManage.mergeDatasetCrossDefault(dto);
            return;
        }
        dto.setIsCross(false);
    }

    /**
     * 批量详情接口返回的是 DatasetTableDTO，也需要补齐数据集级跨源标记。
     */
    private Boolean resolveDatasetCrossFlag(CoreDatasetGroup coreDatasetGroup) {
        if (coreDatasetGroup.getIsCross() != null) {
            return coreDatasetGroup.getIsCross();
        }
        if (!Strings.CI.equals(coreDatasetGroup.getNodeType(), leafType) || StringUtils.isBlank(coreDatasetGroup.getInfo())) {
            return false;
        }
        DatasetGroupInfoDTO dto = new DatasetGroupInfoDTO();
        dto.setNodeType(coreDatasetGroup.getNodeType());
        dto.setUnion(JsonUtil.parseList(coreDatasetGroup.getInfo(), new TypeReference<>() {
        }));
        normalizeDatasetCrossFlag(dto);
        return dto.getIsCross();
    }

    /**
     * 加载数据集并校验当前用户是否拥有创建者权限
     */
    private CoreDatasetGroup requireDatasetAccess(Long datasetId) {
        CoreDatasetGroup dataset = coreDatasetGroupMapper.selectById(datasetId);
        if (dataset == null) {
            CrestException.throwException("resource not exist");
        }
        CrestPermissionUtils.requireCreator(dataset.getCreateBy());
        return dataset;
    }

    /**
     * 加载数据源并校验当前用户是否拥有创建者权限
     */
    private CoreDatasource requireDatasourceAccess(Long datasourceId) {
        CoreDatasource datasource = coreDatasourceMapper.selectById(datasourceId);
        if (datasource == null) {
            CrestException.throwException(Translator.get("i18n_datasource_not_exists"));
        }
        CrestPermissionUtils.requireCreator(datasource.getCreateBy());
        return datasource;
    }

    /**
     * 递归校验目录树中所有子节点的访问权限，确保删除操作不会越权影响子资源
     */
    private void requireDatasetTreeAccess(Long id) {
        requireDatasetAccess(id);
        QueryWrapper<CoreDatasetGroup> wrapper = new QueryWrapper<>();
        wrapper.eq("pid", id);
        List<CoreDatasetGroup> children = coreDatasetGroupMapper.selectList(wrapper);
        if (CollectionUtils.isNotEmpty(children)) {
            for (CoreDatasetGroup child : children) {
                requireDatasetTreeAccess(child.getId());
            }
        }
    }
}
