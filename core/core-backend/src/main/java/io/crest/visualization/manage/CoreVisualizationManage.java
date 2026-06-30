package io.crest.visualization.manage;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.crest.api.visualization.request.DataVisualizationBaseRequest;
import io.crest.api.visualization.request.VisualizationWorkbranchQueryRequest;
import io.crest.api.visualization.vo.VisualizationResourceVO;
import io.crest.chart.dao.ext.mapper.ExtChartViewMapper;
import io.crest.chart.manage.ChartViewManege;
import io.crest.commons.constants.DataVisualizationConstants;
import io.crest.commons.constants.OptConstants;
import io.crest.constant.BusiResourceEnum;
import io.crest.constant.CommonConstants;
import io.crest.exception.CrestException;
import io.crest.model.BusiNodeRequest;
import io.crest.model.BusiNodeVO;
import io.crest.operation.manage.CoreOptRecentManage;
import io.crest.result.ResultCode;
import io.crest.share.manage.ShareManage;
import io.crest.substitute.permissions.auth.PlatformPermissionManage;
import io.crest.system.manage.CoreUserManage;
import io.crest.utils.*;
import io.crest.visualization.dao.auto.entity.DataVisualizationInfo;
import io.crest.visualization.dao.auto.entity.SnapshotDataVisualizationInfo;
import io.crest.visualization.dao.auto.mapper.DataVisualizationInfoMapper;
import io.crest.visualization.dao.auto.mapper.SnapshotDataVisualizationInfoMapper;
import io.crest.visualization.dao.ext.mapper.*;
import io.crest.visualization.dao.ext.po.VisualizationNodePO;
import io.crest.visualization.dao.ext.po.VisualizationResourcePO;
import io.crest.visualization.dto.VisualizationNodeBO;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

// 可视化资源管理服务，负责资源树、保存、删除、快照和工作台最近资源查询
@Component
@Transactional
public class CoreVisualizationManage {


    @Resource
    private CoreVisualiationExtMapper extMapper;

    @Resource
    private CoreUserManage coreUserManage;

    @Resource
    private DataVisualizationInfoMapper mapper;

    @Resource
    private SnapshotDataVisualizationInfoMapper snapshotMapper;

    @Resource
    private ExtVisualizationLinkageMapper linkageMapper;

    @Resource
    private ExtVisualizationLinkJumpMapper linkJumpMapper;

    @Resource
    private ExtVisualizationOuterParamsMapper outerParamsMapper;

    @Resource
    private ExtDataVisualizationMapper extDataVisualizationMapper;

    @Resource
    private CoreOptRecentManage coreOptRecentManage;

    @Resource
    private ExtChartViewMapper extCoreChartMapper;

    @Resource
    private ChartViewManege chartViewManege;

    @Resource
    private ShareManage shareManage;

    @Resource
    private PlatformPermissionManage platformPermissionManage;
    // 查询当前用户可见的可视化资源树
    public List<BusiNodeVO> tree(BusiNodeRequest request) {
        List<VisualizationNodeBO> nodes = new ArrayList<>();
        if (ObjectUtils.isEmpty(request.getLeaf()) || !request.getLeaf()) {
            nodes.add(rootNode());
        }
        QueryWrapper<Object> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("delete_flag", false);
        queryWrapper.ne("pid", -1);
        queryWrapper.eq(ObjectUtils.isNotEmpty(request.getLeaf()), "node_type", ObjectUtils.isNotEmpty(request.getLeaf()) && request.getLeaf() ? "leaf" : "folder");
        queryWrapper.eq("type", request.getBusiFlag());
        String info = CommunityUtils.getInfo();
        if (StringUtils.isNotBlank(info)) {
            queryWrapper.notExists(String.format(info, "core_visualization.id"));
        }
        String resourceType = "dataV".equalsIgnoreCase(request.getBusiFlag()) ? "screen" : "panel";
        String scopeSql = platformPermissionManage.resourceScopeSql(resourceType, "core_visualization.id",
                "core_visualization.create_by", "core_visualization.org_id");
        if (StringUtils.isNotBlank(scopeSql)) {
            queryWrapper.apply(scopeSql);
        }
        // 快照资源表用于编辑引用场景，只展示已发布或已变更的资源
        if (CommonConstants.RESOURCE_TABLE.SNAPSHOT.equals(request.getResourceTable())) {
            queryWrapper.in("status", Arrays.asList(1, 2));
        }
        queryWrapper.orderByDesc("create_time");
        List<VisualizationNodePO> pos = extMapper.queryNodes(queryWrapper);
        if (CollectionUtils.isNotEmpty(pos)) {
            nodes.addAll(pos.stream().map(this::convert).toList());
        }
        return TreeUtils.mergeTree(nodes, BusiNodeVO.class, false);
    }

    // 初始化时清理复制过程中遗留的临时可视化资源
    public void dataVisualizationInit() {
        List<Long> resourceIds= extDataVisualizationMapper.findCopyResource();
        if (CollectionUtils.isNotEmpty(resourceIds)) {
            resourceIds.forEach(this::delete);
        }

    }
    // 删除指定资源及其子资源，并同步清理核心表、快照表和权限索引
    public void delete(Long id) {
        DataVisualizationInfo info = mapper.selectById(id);
        if (ObjectUtils.isEmpty(info)) {
            CrestException.throwException("resource not exist");
        }
        requireVisualizationTreeAccess(id);
        Set<Long> delIds = new LinkedHashSet<>();
        Stack<Long> stack = new Stack<>();
        stack.add(id);
        while (!stack.isEmpty()) {
            Long tempPid = stack.pop();
            if (isTopNode(tempPid)) continue;
            delIds.add(tempPid);
            List<Long> childrenIdList = extMapper.queryChildrenId(tempPid);
            if (CollectionUtils.isNotEmpty(childrenIdList)) {
                childrenIdList.forEach(kid -> {
                    if (!delIds.contains(kid)) {
                        stack.add(kid);
                    }
                });
            }
        }

        if(!ModelUtils.isDesktop()){
            // 非桌面模式下同步删除分享配置
            shareManage.deleteByResource(id);
        }

        // 删除可视化资源主数据和快照数据
        extDataVisualizationMapper.deleteDataVBatch(delIds, CommonConstants.RESOURCE_TABLE.CORE);
        extDataVisualizationMapper.deleteDataVBatch(delIds, CommonConstants.RESOURCE_TABLE.SNAPSHOT);
        // 删除资源关联的图表信息
        extDataVisualizationMapper.deleteViewsBatch(delIds, CommonConstants.RESOURCE_TABLE.CORE);
        extDataVisualizationMapper.deleteViewsBatch(delIds, CommonConstants.RESOURCE_TABLE.SNAPSHOT);
        delIds.forEach(delId -> platformPermissionManage.deleteResource(resourceType(info.getType()), String.valueOf(delId)));

        coreOptRecentManage.saveOpt(id, OptConstants.OPT_RESOURCE_TYPE.VISUALIZATION, OptConstants.OPT_TYPE.DELETE);
    }
    // 移动资源或更新资源基础位置，并同步核心表和快照表
    public void move(DataVisualizationBaseRequest request) {
        if (!request.getMoveFromUpdate()) {
            requireVisualizationAccess(request.getId());
            if (ObjectUtils.isNotEmpty(request.getPid()) && !isTopNode(request.getPid())) {
                requireVisualizationAccess(request.getPid());
            }
            DataVisualizationInfo visualizationInfo = new DataVisualizationInfo();
            BeanUtils.copyBean(visualizationInfo, request);
            if (ObjectUtils.isEmpty(visualizationInfo.getId())) {
                CrestException.throwException("resource not exist");
            }
            visualizationInfo.setUpdateTime(System.currentTimeMillis());
            SnapshotDataVisualizationInfo snapshotVisualizationInfo = new SnapshotDataVisualizationInfo();
            BeanUtils.copyBean(snapshotVisualizationInfo, visualizationInfo);
            coreOptRecentManage.saveOpt(visualizationInfo.getId(), OptConstants.OPT_RESOURCE_TYPE.VISUALIZATION, OptConstants.OPT_TYPE.UPDATE);
            mapper.updateById(visualizationInfo);
            snapshotMapper.updateById(snapshotVisualizationInfo);
        }
    }
    // 内部新建资源入口，先校验父级访问权限再保存
    public Long innerSave(DataVisualizationInfo visualizationInfo) {
        if (ObjectUtils.isNotEmpty(visualizationInfo.getPid()) && !isTopNode(visualizationInfo.getPid()) && !Objects.equals(visualizationInfo.getPid(), -1L)) {
            requireVisualizationAccess(visualizationInfo.getPid());
        }
        visualizationInfo.setVersion(3);
        return preInnerSave(visualizationInfo);
    }

    // 执行资源新建落库，并创建对应快照和权限索引
    public Long preInnerSave(DataVisualizationInfo visualizationInfo) {
        requireName(visualizationInfo);
        if (visualizationInfo.getId() == null) {
            Long id = IDUtils.snowID();
            visualizationInfo.setId(id);
        }
        visualizationInfo.setDeleteFlag(DataVisualizationConstants.DELETE_FLAG.AVAILABLE);
        visualizationInfo.setStatus(visualizationInfo.getStatus());
        visualizationInfo.setCreateBy(AuthUtils.getUser().getUserId().toString());
        visualizationInfo.setUpdateBy(AuthUtils.getUser().getUserId().toString());
        visualizationInfo.setCreateTime(System.currentTimeMillis());
        visualizationInfo.setUpdateTime(System.currentTimeMillis());
        visualizationInfo.setOrgId(AuthUtils.getUser().getDefaultOid());
        mapper.insert(visualizationInfo);
        // 新建资源时同步插入快照记录
        SnapshotDataVisualizationInfo snapshotVisualizationInfo = new SnapshotDataVisualizationInfo();
        BeanUtils.copyBean(snapshotVisualizationInfo, visualizationInfo);
        snapshotMapper.insert(snapshotVisualizationInfo);
        platformPermissionManage.upsertResource(resourceType(visualizationInfo.getType()), String.valueOf(visualizationInfo.getId()),
                visualizationInfo.getOrgId(), AuthUtils.getUser().getUserId(), visualizationInfo.getName(),
                visualizationInfo.getCreateTime(), visualizationInfo.getUpdateTime());
        coreOptRecentManage.saveOpt(visualizationInfo.getId(), OptConstants.OPT_RESOURCE_TYPE.VISUALIZATION, OptConstants.OPT_TYPE.NEW);
        return visualizationInfo.getId();
    }
    // 编辑资源基础信息，并保持核心表、快照表和权限索引一致
    public void innerEdit(DataVisualizationInfo visualizationInfo) {
        requireVisualizationAccess(visualizationInfo.getId());
        keepExistingNameIfMissing(visualizationInfo);
        // 快照和主表保持资源名称、状态和父级一致
        visualizationInfo.setUpdateTime(System.currentTimeMillis());
        visualizationInfo.setUpdateBy(AuthUtils.getUser().getUserId().toString());
        visualizationInfo.setVersion(3);
        // 更新快照表
        SnapshotDataVisualizationInfo snapshotVisualizationInfo = new SnapshotDataVisualizationInfo();
        BeanUtils.copyBean(snapshotVisualizationInfo, visualizationInfo);
        snapshotMapper.updateById(snapshotVisualizationInfo);
        // 更新核心表
        DataVisualizationInfo coreVisualizationInfo = new DataVisualizationInfo();
        coreVisualizationInfo.setId(visualizationInfo.getId());
        coreVisualizationInfo.setStatus(visualizationInfo.getStatus());
        coreVisualizationInfo.setPid(visualizationInfo.getPid());
        coreVisualizationInfo.setContentId(visualizationInfo.getContentId());
        coreVisualizationInfo.setName(visualizationInfo.getName());
        coreVisualizationInfo.setUpdateTime(System.currentTimeMillis());
        coreVisualizationInfo.setUpdateBy(AuthUtils.getUser().getUserId().toString());
        coreVisualizationInfo.setVersion(3);
        mapper.updateById(coreVisualizationInfo);
        platformPermissionManage.upsertResource(resourceType(visualizationInfo.getType()), String.valueOf(visualizationInfo.getId()),
                AuthUtils.getUser().getDefaultOid(), AuthUtils.getUser().getUserId(), visualizationInfo.getName(),
                visualizationInfo.getCreateTime(), visualizationInfo.getUpdateTime());
        coreOptRecentManage.saveOpt(visualizationInfo.getId(), OptConstants.OPT_RESOURCE_TYPE.VISUALIZATION, OptConstants.OPT_TYPE.UPDATE);
    }

    // 将业务类型转换为权限系统使用的资源类型
    private String resourceType(String type) {
        return Strings.CI.equals(type, "dataV") ? "screen" : "panel";
    }

    // 校验资源名称不能为空
    private void requireName(DataVisualizationInfo visualizationInfo) {
        if (StringUtils.isBlank(visualizationInfo.getName())) {
            CrestException.throwException("资源名称不能为空");
        }
    }

    // 编辑请求缺少名称时回填已有名称，避免历史调用误清空资源名称
    private void keepExistingNameIfMissing(DataVisualizationInfo visualizationInfo) {
        if (StringUtils.isNotBlank(visualizationInfo.getName())) {
            return;
        }
        DataVisualizationInfo current = mapper.selectById(visualizationInfo.getId());
        if (current != null && StringUtils.isNotBlank(current.getName())) {
            visualizationInfo.setName(current.getName());
            return;
        }
        CrestException.throwException("资源名称不能为空");
    }

    // 校验当前用户对指定资源具有创建者访问权限
    private DataVisualizationInfo requireVisualizationAccess(Long id) {
        if (ObjectUtils.isEmpty(id)) {
            CrestException.throwException("resource not exist");
        }
        DataVisualizationInfo info = mapper.selectById(id);
        if (ObjectUtils.isEmpty(info)) {
            CrestException.throwException("resource not exist");
        }
        CrestPermissionUtils.requireCreator(info.getCreateBy());
        return info;
    }

    // 递归校验当前用户对资源树全部节点的访问权限
    private void requireVisualizationTreeAccess(Long id) {
        requireVisualizationAccess(id);
        List<Long> childrenIdList = extMapper.queryChildrenId(id);
        if (CollectionUtils.isNotEmpty(childrenIdList)) {
            childrenIdList.forEach(this::requireVisualizationTreeAccess);
        }
    }

    // 判断节点是否为顶层根节点
    private boolean isTopNode(Long pid) {
        return ObjectUtils.isEmpty(pid) || pid.equals(0L);
    }

    // 构造资源树根节点
    private VisualizationNodeBO rootNode() {
        return new VisualizationNodeBO(0L, "root", false, 7, -1L, 0, 1);
    }

    // 将数据库节点转换为前端资源树节点，并附带当前用户权限权重
    private VisualizationNodeBO convert(VisualizationNodePO po) {
        int weight = platformPermissionManage.resourceWeight(resourceType(po.getType()), String.valueOf(po.getId()), po.getCreateBy(), po.getOrgId());
        return new VisualizationNodeBO(po.getId(), po.getName(), Strings.CS.equals(po.getNodeType(), "leaf"), weight, po.getPid(), po.getExtraFlag(), po.getExtraFlag1());
    }

    // 要求当前用户对资源具备管理权限
    public void requireVisualizationManage(DataVisualizationInfo info) {
        if (ObjectUtils.isEmpty(info)) {
            CrestException.throwException("resource not exist");
        }
        int weight = platformPermissionManage.resourceWeight(resourceType(info.getType()), String.valueOf(info.getId()), info.getCreateBy(), info.getOrgId());
        if (weight < 7) {
            CrestException.throwException(ResultCode.PERMISSION_NO_ACCESS.code(), "当前用户无权访问该资源");
        }
    }

    // 获取当前服务代理，保证内部事务方法通过代理调用
    public CoreVisualizationManage proxy() {
        return CommonBeanFactory.getBean(this.getClass());
    }
    // 查询工作台可视化资源分页，并转换为前端视图对象
    public IPage<VisualizationResourceVO> query(int pageNum, int pageSize, VisualizationWorkbranchQueryRequest request) {
        IPage<VisualizationResourcePO> visualizationResourcePOPageIPage = proxy().queryVisualizationPage(pageNum, pageSize, request);
        if (ObjectUtils.isEmpty(visualizationResourcePOPageIPage)) {
            return null;
        }
        List<VisualizationResourceVO> vos = proxy().formatResult(visualizationResourcePOPageIPage.getRecords());
        IPage<VisualizationResourceVO> iPage = new Page<>();
        iPage.setCurrent(visualizationResourcePOPageIPage.getCurrent());
        iPage.setPages(visualizationResourcePOPageIPage.getPages());
        iPage.setSize(visualizationResourcePOPageIPage.getSize());
        iPage.setTotal(visualizationResourcePOPageIPage.getTotal());
        iPage.setRecords(vos);
        return iPage;
    }

    // 将资源持久化对象转换为工作台资源视图对象
    List<VisualizationResourceVO> formatResult(List<VisualizationResourcePO> pos) {
        if (CollectionUtils.isEmpty(pos)) {
            return new ArrayList<>();
        }
        return pos.stream().map(po ->
                new VisualizationResourceVO(
                        po.getId(), po.getResourceId(), po.getName(),
                        po.getType(), coreUserManage.getUserName(po.getCreator()), coreUserManage.getUserName(po.getLastEditor()), po.getLastEditTime(),
                        po.getFavorite(), 9, po.getExtFlag())).toList();
    }

    // 查询当前用户最近访问的可视化资源分页
    public IPage<VisualizationResourcePO> queryVisualizationPage(int goPage, int pageSize, VisualizationWorkbranchQueryRequest request) {
        Long uid = AuthUtils.getUser().getUserId();
        Map<String, Object> params = new HashMap<>();
        if (StringUtils.isNotBlank(request.getType())) {
            assertBusiResourceType(request.getType());
            params.put("type", request.getType());
        }
        String info = CommunityUtils.getInfo();
        if (StringUtils.isNotBlank(info)) {
            params.put("info", info);
        }
        params.put("isAsc", request.isAsc());
        Page<VisualizationResourcePO> page = new Page<>(goPage, pageSize);
        return extDataVisualizationMapper.findRecent(page, uid, request.getKeyword(), params);
    }

    // 校验工作台资源类型是否为系统支持的业务资源
    private void assertBusiResourceType(String type) {
        try {
            BusiResourceEnum.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            CrestException.throwException("type is invalid");
        }
    }

    // 删除指定资源的快照数据
    @Transactional
    public void removeSnapshot(Long dvId) {
        if (dvId != null) {
            // 清理旧快照数据
            Set<Long> dvIds = new HashSet<>();
            dvIds.add(dvId);
            extDataVisualizationMapper.deleteDataVBatch(dvIds, CommonConstants.RESOURCE_TABLE.SNAPSHOT);
            extCoreChartMapper.deleteViewsBySceneId(dvId, CommonConstants.RESOURCE_TABLE.SNAPSHOT);
            linkageMapper.deleteViewLinkageFieldSnapshot(dvId, null);
            linkageMapper.deleteViewLinkageSnapshot(dvId, null);
            linkJumpMapper.deleteJumpTargetViewInfoWithVisualizationSnapshot(dvId);
            linkJumpMapper.deleteJumpInfoWithVisualizationSnapshot(dvId);
            linkJumpMapper.deleteJumpWithVisualizationSnapshot(dvId);
            outerParamsMapper.deleteOuterParamsTargetWithVisualizationIdSnapshot(dvId.toString());
            outerParamsMapper.deleteOuterParamsInfoWithVisualizationIdSnapshot(dvId.toString());
            outerParamsMapper.deleteOuterParamsWithVisualizationIdSnapshot(dvId.toString());
            // 清理快照阈值告警
            chartViewManege.removeThreshold(dvId, CommonConstants.RESOURCE_TABLE.SNAPSHOT);

        }
    }

    // 删除指定资源的核心数据
    @Transactional
    public void removeDvCore(Long dvId) {
        if (dvId != null) {
            // 清理核心资源数据
            Set<Long> dvIds = new HashSet<>();
            dvIds.add(dvId);
            extDataVisualizationMapper.deleteDataVBatch(dvIds, CommonConstants.RESOURCE_TABLE.CORE);
            extCoreChartMapper.deleteViewsBySceneId(dvId, CommonConstants.RESOURCE_TABLE.CORE);
            linkageMapper.deleteViewLinkageField(dvId, null);
            linkageMapper.deleteViewLinkage(dvId, null);
            linkJumpMapper.deleteJumpTargetViewInfoWithVisualization(dvId);
            linkJumpMapper.deleteJumpInfoWithVisualization(dvId);
            linkJumpMapper.deleteJumpWithVisualization(dvId);
            outerParamsMapper.deleteOuterParamsTargetWithVisualizationId(dvId.toString());
            outerParamsMapper.deleteOuterParamsInfoWithVisualizationId(dvId.toString());
            outerParamsMapper.deleteOuterParamsWithVisualizationId(dvId.toString());
            // 清理核心阈值告警
            chartViewManege.removeThreshold(dvId, CommonConstants.RESOURCE_TABLE.CORE);
        }
    }

    // 用核心数据重建快照数据
    @Transactional
    public void dvSnapshotRecover(Long dvId) {
        // 先清理旧快照数据，再从核心表复制最新数据
        CoreVisualizationManage proxy = CommonBeanFactory.proxy(this.getClass());
        assert proxy != null;
        proxy.removeSnapshot(dvId);
        // 导入新数据
        extDataVisualizationMapper.snapshotDataV(dvId);
        extDataVisualizationMapper.snapshotViews(dvId);
        extDataVisualizationMapper.snapshotLinkJumpTargetViewInfo(dvId);
        extDataVisualizationMapper.snapshotLinkJumpInfo(dvId);
        extDataVisualizationMapper.snapshotLinkJump(dvId);
        extDataVisualizationMapper.snapshotLinkageField(dvId);
        extDataVisualizationMapper.snapshotLinkage(dvId);
        extDataVisualizationMapper.snapshotOuterParamsTargetViewInfo(dvId);
        extDataVisualizationMapper.snapshotOuterParamsInfo(dvId);
        extDataVisualizationMapper.snapshotOuterParams(dvId);
        // 恢复快照阈值告警
        chartViewManege.restoreThreshold(dvId, CommonConstants.RESOURCE_TABLE.SNAPSHOT);
    }

    // 将快照数据恢复到核心资源数据
    @Transactional
    public void dvRestore(Long dvId) {
        extDataVisualizationMapper.restoreDataV(dvId);
        extDataVisualizationMapper.restoreViews(dvId);
        extDataVisualizationMapper.restoreLinkJumpTargetViewInfo(dvId);
        extDataVisualizationMapper.restoreLinkJumpInfo(dvId);
        extDataVisualizationMapper.restoreLinkJump(dvId);
        extDataVisualizationMapper.restoreLinkageField(dvId);
        extDataVisualizationMapper.restoreLinkage(dvId);
        extDataVisualizationMapper.restoreOuterParamsTargetViewInfo(dvId);
        extDataVisualizationMapper.restoreOuterParamsInfo(dvId);
        extDataVisualizationMapper.restoreOuterParams(dvId);
        // 恢复核心阈值告警
        chartViewManege.restoreThreshold(dvId, CommonConstants.RESOURCE_TABLE.CORE);
    }

}
