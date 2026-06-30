package io.crest.visualization.server;

import io.crest.api.commons.BaseRspModel;
import io.crest.api.visualization.VisualizationLinkageApi;
import io.crest.api.visualization.dto.LinkageInfoDTO;
import io.crest.api.visualization.dto.VisualizationLinkageDTO;
import io.crest.api.visualization.request.VisualizationLinkageRequest;
import io.crest.api.visualization.vo.VisualizationLinkageFieldVO;
import io.crest.auth.CrestLinkPermit;
import io.crest.chart.dao.auto.entity.CoreChartView;
import io.crest.chart.dao.auto.mapper.CoreChartViewMapper;
import io.crest.constant.CommonConstants;
import io.crest.utils.BeanUtils;
import io.crest.utils.IDUtils;
import io.crest.visualization.dao.auto.entity.*;
import io.crest.visualization.dao.auto.mapper.*;
import io.crest.visualization.dao.ext.mapper.ExtVisualizationLinkageMapper;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理可视化组件联动配置，负责联动关系的查询、保存和快照同步。
 */
@RestController
@RequestMapping("linkage")
public class VisualizationLinkageService implements VisualizationLinkageApi {

    /**
     * 联动扩展查询 Mapper
     */
    @Resource
    private ExtVisualizationLinkageMapper extVisualizationLinkageMapper;

    /**
     * 正式联动字段 Mapper
     */
    @Resource
    private VisualizationLinkageFieldMapper visualizationLinkageFieldMapper;

    /**
     * 快照联动字段 Mapper
     */
    @Resource
    private SnapshotVisualizationLinkageFieldMapper snapshotVisualizationLinkageFieldMapper;

    /**
     * 正式联动关系 Mapper
     */
    @Resource
    private VisualizationLinkageMapper visualizationLinkageMapper;

    /**
     * 快照联动关系 Mapper
     */
    @Resource
    private SnapshotVisualizationLinkageMapper snapshotVisualizationLinkageMapper;

    /**
     * 可视化资源信息 Mapper
     */
    @Resource
    private DataVisualizationInfoMapper dataVisualizationInfoMapper;

    /**
     * 正式图表视图 Mapper
     */
    @Resource
    private CoreChartViewMapper coreChartViewMapper;

    /**
     * 快照图表视图 Mapper
     */
    @Resource
    private SnapshotCoreChartViewMapper snapshotCoreChartViewMapper;

    /**
     * 按目标视图编号聚合指定来源视图的联动配置
     */
    @Override
    public Map<String, VisualizationLinkageDTO> viewLinkageGather(VisualizationLinkageRequest request) {
        if (CollectionUtils.isNotEmpty(request.getTargetViewIds())) {
            List<VisualizationLinkageDTO> linkageDTOList = null;
            if (CommonConstants.RESOURCE_TABLE.SNAPSHOT.equals(request.getResourceTable())) {
                linkageDTOList = extVisualizationLinkageMapper.viewLinkageGatherSnapshot(request.getDvId(), request.getSourceViewId(), request.getTargetViewIds());
            } else {
                linkageDTOList =  extVisualizationLinkageMapper.viewLinkageGather(request.getDvId(), request.getSourceViewId(), request.getTargetViewIds());
            }
            return linkageDTOList.stream().collect(Collectors.toMap(targetViewId -> String.valueOf(targetViewId), PanelViewLinkageDTO -> PanelViewLinkageDTO));
        }
        return new HashMap<>();
    }

    /**
     * 以数组形式查询指定来源视图的联动配置
     */
    @Override
    public List<VisualizationLinkageDTO> viewLinkageGatherArray(VisualizationLinkageRequest request) {
        if (CommonConstants.RESOURCE_TABLE.SNAPSHOT.equals(request.getResourceTable())) {
            return extVisualizationLinkageMapper.viewLinkageGatherSnapshot(request.getDvId(), request.getSourceViewId(), request.getTargetViewIds());
        } else {
            return extVisualizationLinkageMapper.viewLinkageGather(request.getDvId(), request.getSourceViewId(), request.getTargetViewIds());
        }
    }

    /**
     * 保存来源视图的联动关系到快照表
     */
    @Override
    @Transactional
    public BaseRspModel saveLinkage(VisualizationLinkageRequest request) {
        // 向快照中保存
        Long updateTime = System.currentTimeMillis();
        List<VisualizationLinkageDTO> linkageInfo = request.getLinkageInfo();
        Long sourceViewId = request.getSourceViewId();
        Long dvId = request.getDvId();

        Assert.notNull(sourceViewId, "source View ID can not be null");
        Assert.notNull(dvId, "dvId can not be null");

        // 清理原有关系
        extVisualizationLinkageMapper.deleteViewLinkageFieldSnapshot(dvId, sourceViewId);
        extVisualizationLinkageMapper.deleteViewLinkageSnapshot(dvId, sourceViewId);

        // 重新建立关系
        for (VisualizationLinkageDTO linkageDTO : linkageInfo) {
            // 跳过来源视图自身，避免形成自关联
            if (sourceViewId.equals(linkageDTO.getTargetViewId())) {
                continue;
            }
            List<VisualizationLinkageFieldVO> linkageFields = linkageDTO.getLinkageFields();
            Long linkageId = IDUtils.snowID();
            SnapshotVisualizationLinkage linkage = new SnapshotVisualizationLinkage();
            linkage.setId(linkageId);
            linkage.setDvId(dvId);
            linkage.setSourceViewId(sourceViewId);
            linkage.setTargetViewId(linkageDTO.getTargetViewId());
            linkage.setUpdatePeople("");
            linkage.setUpdateTime(updateTime);
            linkage.setLinkageActive(linkageDTO.getLinkageActive());
            snapshotVisualizationLinkageMapper.insert(linkage);
            if (CollectionUtils.isNotEmpty(linkageFields) && linkageDTO.getLinkageActive()) {
                linkageFields.forEach(linkageField -> {
                    linkageField.setId(IDUtils.snowID());
                    linkageField.setLinkageId(linkageId);
                    linkageField.setUpdateTime(updateTime);
                    SnapshotVisualizationLinkageField fieldInsert = new SnapshotVisualizationLinkageField();
                    snapshotVisualizationLinkageFieldMapper.insert(BeanUtils.copyBean(fieldInsert, linkageField));
                });
            }
        }
        return new BaseRspModel();
    }

    /**
     * 查询可视化资源内所有视图联动的来源和目标关系
     */
    @CrestLinkPermit
    @Override
    public Map<String, List<String>> getVisualizationAllLinkageInfo(Long dvId, String resourceTable) {
        List<LinkageInfoDTO> info = null;
        if (CommonConstants.RESOURCE_TABLE.SNAPSHOT.equals(resourceTable)) {
            info = extVisualizationLinkageMapper.getPanelAllLinkageInfoSnapshot(dvId);
        }else{
            info = extVisualizationLinkageMapper.getPanelAllLinkageInfo(dvId);
        }
        return Optional.ofNullable(info).orElse(new ArrayList<>()).stream().collect(Collectors.toMap(LinkageInfoDTO::getSourceInfo, LinkageInfoDTO::getTargetInfoList));
    }

    /**
     * 更新来源视图联动启用状态，并返回最新联动关系
     */
    @Override
    public Map updateLinkageActive(VisualizationLinkageRequest request) {
        SnapshotCoreChartView coreChartView = new SnapshotCoreChartView();
        coreChartView.setId(request.getSourceViewId());
        coreChartView.setLinkageActive(request.getActiveStatus());
        snapshotCoreChartViewMapper.updateById(coreChartView);
        return getVisualizationAllLinkageInfo(request.getDvId(),CommonConstants.RESOURCE_TABLE.SNAPSHOT);
    }

    /**
     * 删除来源视图在快照表中的联动关系
     */
    @Override
    public void deleteLinkage(VisualizationLinkageRequest request) {
        // 清理原有关系
        extVisualizationLinkageMapper.deleteViewLinkageFieldSnapshot(request.getDvId(), request.getSourceViewId());
        extVisualizationLinkageMapper.deleteViewLinkageSnapshot(request.getDvId(), request.getSourceViewId());
    }
}
