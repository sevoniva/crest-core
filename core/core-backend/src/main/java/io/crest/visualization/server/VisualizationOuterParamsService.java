package io.crest.visualization.server;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import io.crest.api.dataset.vo.CoreDatasetGroupVO;
import io.crest.api.dataset.vo.CoreDatasetTableFieldVO;
import io.crest.api.visualization.VisualizationOuterParamsApi;
import io.crest.api.visualization.dto.VisualizationOuterParamsDTO;
import io.crest.api.visualization.dto.VisualizationOuterParamsInfoDTO;
import io.crest.api.visualization.response.VisualizationOuterParamsBaseResponse;
import io.crest.auth.CrestLinkPermit;
import io.crest.constant.CommonConstants;
import io.crest.dataset.dao.auto.entity.CoreDatasetTable;
import io.crest.dataset.dao.auto.mapper.CoreDatasetTableMapper;
import io.crest.constant.FieldTypeConstants;
import io.crest.dataset.utils.DatasetParameterFieldId;
import io.crest.dataset.utils.FieldUtils;
import io.crest.extensions.view.dto.SqlVariableDetails;
import io.crest.utils.BeanUtils;
import io.crest.utils.JsonUtil;
import io.crest.visualization.dao.auto.entity.*;
import io.crest.visualization.dao.auto.mapper.*;
import io.crest.visualization.dao.ext.mapper.ExtVisualizationOuterParamsMapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理可视化外部参数配置，支持编辑态与快照态的数据映射。
 */
@RestController
@RequestMapping("outer-params")
public class VisualizationOuterParamsService implements VisualizationOuterParamsApi {

    /**
     * 外部参数扩展查询 Mapper
     */
    @Resource
    private ExtVisualizationOuterParamsMapper extOuterParamsMapper;
    /**
     * 编辑态外部参数 Mapper
     */
    @Resource
    private VisualizationOuterParamsMapper outerParamsMapper;
    /**
     * 快照态外部参数 Mapper
     */
    @Resource
    private SnapshotVisualizationOuterParamsMapper snapshotOuterParamsMapper;

    /**
     * 编辑态外部参数明细 Mapper
     */
    @Resource
    private VisualizationOuterParamsInfoMapper outerParamsInfoMapper;

    /**
     * 快照态外部参数明细 Mapper
     */
    @Resource
    private SnapshotVisualizationOuterParamsInfoMapper snapshotOuterParamsInfoMapper;

    /**
     * 编辑态目标图表映射 Mapper
     */
    @Resource
    private VisualizationOuterParamsTargetViewInfoMapper outerParamsTargetViewInfoMapper;

    /**
     * 快照态目标图表映射 Mapper
     */
    @Resource
    private SnapshotVisualizationOuterParamsTargetViewInfoMapper snapshotOuterParamsTargetViewInfoMapper;

    /**
     * 数据集表 Mapper，用于补充 SQL 变量字段
     */
    @Resource
    private CoreDatasetTableMapper coreDatasetTableMapper;
    /**
     * 可视化服务，用于查询当前可用图表
     */
    @Autowired
    private DataVisualizationServer dataVisualizationServer;
    /**
     * 快照可视化信息 Mapper
     */
    @Autowired
    private SnapshotDataVisualizationInfoMapper snapshotDataVisualizationInfoMapper;


    /**
     * 按可视化 ID 查询快照态外部参数配置
     */
    @Override
    public VisualizationOuterParamsDTO queryWithVisualizationId(String visualizationId) {
        VisualizationOuterParamsDTO visualizationOuterParamsDTO = extOuterParamsMapper.queryWithVisualizationIdSnapshot(visualizationId);
        return visualizationOuterParamsDTO;
    }

    /**
     * 更新可视化外部参数设置，并重建快照态参数明细和目标图表关系
     */
    @Override
    public void updateOuterParamsSet(VisualizationOuterParamsDTO outerParamsDTO) {
        String visualizationId = outerParamsDTO.getVisualizationId();
        Assert.notNull(visualizationId, "visualizationId cannot be null");
        Map<String,String> paramsInfoNameIdMap = new HashMap<>();
        List<SnapshotVisualizationOuterParamsInfo> paramsInfoNameIdList = extOuterParamsMapper.getVisualizationOuterParamsInfoBase(visualizationId);
        if(!CollectionUtils.isEmpty(paramsInfoNameIdList)){
            paramsInfoNameIdMap = paramsInfoNameIdList.stream()
                    .collect(Collectors.toMap(SnapshotVisualizationOuterParamsInfo::getParamName, SnapshotVisualizationOuterParamsInfo::getParamsInfoId));
        }
        //清理原有数据
        extOuterParamsMapper.deleteOuterParamsTargetWithVisualizationIdSnapshot(visualizationId);
        extOuterParamsMapper.deleteOuterParamsInfoWithVisualizationIdSnapshot(visualizationId);
        extOuterParamsMapper.deleteOuterParamsWithVisualizationIdSnapshot(visualizationId);
        if(CollectionUtils.isEmpty(outerParamsDTO.getOuterParamsInfoArray())){
            return;
        }
        // 插入新的数据
        String paramsId = UUID.randomUUID().toString();
        outerParamsDTO.setParamsId(paramsId);
        SnapshotVisualizationOuterParams newOuterParams = new SnapshotVisualizationOuterParams();
        BeanUtils.copyBean(newOuterParams, outerParamsDTO);
        snapshotOuterParamsMapper.insert(newOuterParams);
        Map<String, String> finalParamsInfoNameIdMap = paramsInfoNameIdMap;
        Optional.ofNullable(outerParamsDTO.getOuterParamsInfoArray()).orElse(new ArrayList<>()).forEach(outerParamsInfo -> {
            String paramsInfoId = finalParamsInfoNameIdMap.get(outerParamsInfo.getParamName());
            if(StringUtils.isEmpty(paramsInfoId)){
                paramsInfoId = UUID.randomUUID().toString();
            }
            outerParamsInfo.setParamsInfoId(paramsInfoId);
            outerParamsInfo.setParamsId(paramsId);
            SnapshotVisualizationOuterParamsInfo newOuterParamsInfo = new SnapshotVisualizationOuterParamsInfo();
            BeanUtils.copyBean(newOuterParamsInfo, outerParamsInfo);
            snapshotOuterParamsInfoMapper.insert(newOuterParamsInfo);
            String finalParamsInfoId = paramsInfoId;
            Optional.ofNullable(outerParamsInfo.getTargetViewInfoList()).orElse(new ArrayList<>()).forEach(targetViewInfo -> {
                String targetViewInfoId = UUID.randomUUID().toString();
                targetViewInfo.setTargetId(targetViewInfoId);
                targetViewInfo.setParamsInfoId(finalParamsInfoId);
                SnapshotVisualizationOuterParamsTargetViewInfo newOuterParamsTargetViewInfo = new SnapshotVisualizationOuterParamsTargetViewInfo();
                BeanUtils.copyBean(newOuterParamsTargetViewInfo, targetViewInfo);
                snapshotOuterParamsTargetViewInfoMapper.insert(newOuterParamsTargetViewInfo);
            });
        });

    }

    /**
     * 查询外部参数映射详情，分享链接访问时会校验资源权限
     */
    @CrestLinkPermit
    @Override
    public VisualizationOuterParamsBaseResponse getOuterParamsInfo(String visualizationId) {
        List<VisualizationOuterParamsInfoDTO> result = extOuterParamsMapper.getVisualizationOuterParamsInfo(visualizationId);
        return new VisualizationOuterParamsBaseResponse(Optional.ofNullable(result).orElse(new ArrayList<>()).stream().collect(Collectors.toMap(VisualizationOuterParamsInfoDTO::getSourceInfo, VisualizationOuterParamsInfoDTO::getTargetInfoList)),
                Optional.ofNullable(result).orElse(new ArrayList<>()).stream().collect(Collectors.toMap(VisualizationOuterParamsInfoDTO::getSourceInfo, paramsInfo -> paramsInfo))
        );
    }

    /**
     * 查询可视化关联的数据集字段，并补充 SQL 变量字段
     */
    @Override
    public List<CoreDatasetGroupVO> queryDsWithVisualizationId(String visualizationId) {
        List<CoreDatasetGroupVO> result = extOuterParamsMapper.queryDsWithVisualizationId(visualizationId);
        if (!CollectionUtils.isEmpty(result)) {
            List<Long> activeViewIds = dataVisualizationServer.getEnabledViewIds(Long.valueOf(visualizationId), CommonConstants.RESOURCE_TABLE.SNAPSHOT);
            result.forEach(coreDatasetGroupVO -> {
                // 过滤已删除的图表
                if(!CollectionUtils.isEmpty(coreDatasetGroupVO.getDatasetViews())){
                    coreDatasetGroupVO.setDatasetViews(coreDatasetGroupVO.getDatasetViews().stream().filter(item ->activeViewIds.contains(item.getChartId())).toList());
                }
                List<CoreDatasetTableFieldVO> fields = coreDatasetGroupVO.getDatasetFields();
                QueryWrapper<CoreDatasetTable> wrapper = new QueryWrapper<>();
                wrapper.eq("dataset_group_id", coreDatasetGroupVO.getId());
                List<CoreDatasetTable> tableResult = coreDatasetTableMapper.selectList(wrapper);
                if (!CollectionUtils.isEmpty(tableResult)) {
                    tableResult.forEach(coreDatasetTable -> {
                        String sqlVarDetail = coreDatasetTable.getSqlVariableDetails();
                        if (StringUtils.isNotEmpty(sqlVarDetail)) {
                            TypeReference<List<SqlVariableDetails>> listTypeReference = new TypeReference<List<SqlVariableDetails>>() {
                            };
                            List<SqlVariableDetails> defaultsSqlVariableDetails = JsonUtil.parseList(sqlVarDetail, listTypeReference);
                            defaultsSqlVariableDetails.forEach(sqlVariableDetails -> {
                                String varFieldId = DatasetParameterFieldId.build(coreDatasetTable.getId(), sqlVariableDetails.getVariableName());
                                fields.add(new CoreDatasetTableFieldVO(varFieldId, sqlVariableDetails.getVariableName(), FieldUtils.resolveFieldType(sqlVariableDetails.getType().get(0).contains("DATETIME") ? "DATETIME" : sqlVariableDetails.getType().get(0))));
                            });
                        }
                    });
                }
            });
        }
        return result;
    }
}
