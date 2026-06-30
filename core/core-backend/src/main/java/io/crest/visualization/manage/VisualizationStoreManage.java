package io.crest.visualization.manage;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.crest.api.visualization.request.VisualizationStoreRequest;
import io.crest.api.visualization.request.VisualizationWorkbranchQueryRequest;
import io.crest.api.visualization.vo.VisualizationStoreVO;
import io.crest.constant.BusiResourceEnum;
import io.crest.exception.CrestException;
import io.crest.metadata.MetadataDbDialect;
import io.crest.metadata.MetadataDbDialects;
import io.crest.system.manage.CoreUserManage;
import io.crest.utils.AuthUtils;
import io.crest.utils.CommonBeanFactory;
import io.crest.utils.CommunityUtils;
import io.crest.utils.IDUtils;
import io.crest.visualization.dao.auto.entity.CoreStore;
import io.crest.visualization.dao.auto.mapper.CoreStoreMapper;
import io.crest.visualization.dao.ext.mapper.CoreStoreExtMapper;
import io.crest.visualization.dao.ext.po.StorePO;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 管理可视化资源收藏的写入、查询和结果转换
 */
@Component
public class VisualizationStoreManage {

    @Resource
    private CoreStoreMapper coreStoreMapper;

    @Resource
    private CoreStoreExtMapper coreStoreExtMapper;

    @Resource
    private CoreUserManage coreUserManage;

    @Resource
    private Environment environment;

    /**
     * 切换当前用户对指定可视化资源的收藏状态
     */
    public void execute(VisualizationStoreRequest request) {
        Long resourceId = request.getId();
        Long uid = AuthUtils.getUser().getUserId();
        if (favorited(resourceId)) {
            QueryWrapper<CoreStore> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("resource_id", resourceId);
            queryWrapper.eq("uid", uid);
            coreStoreMapper.delete(queryWrapper);
            return;
        }
        String type = request.getType();
        BusiResourceEnum busiResourceEnum = busiResourceType(type);
        CoreStore coreStore = new CoreStore();
        coreStore.setId(IDUtils.snowID());
        coreStore.setTime(System.currentTimeMillis());
        coreStore.setUid(uid);
        coreStore.setResourceId(resourceId);
        coreStore.setResourceType(busiResourceEnum.getFlag());
        coreStoreMapper.insert(coreStore);
    }

    /**
     * 判断当前用户是否已收藏指定资源
     */
    public Boolean favorited(Long resourceId) {
        QueryWrapper<CoreStore> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("resource_id", resourceId);
        queryWrapper.eq("uid", AuthUtils.getUser().getUserId());
        return coreStoreMapper.exists(queryWrapper);
    }
    /**
     * 分页查询当前用户收藏的可视化资源
     */
    public IPage<VisualizationStoreVO> query(int pageNum, int pageSize, VisualizationWorkbranchQueryRequest request) {
        IPage<StorePO> storePOIPage = proxy().queryStorePage(pageNum, pageSize, request);
        if (ObjectUtils.isEmpty(storePOIPage)) return null;
        List<VisualizationStoreVO> vos = proxy().formatResult(storePOIPage.getRecords());
        IPage<VisualizationStoreVO> ipage = new Page<>();
        ipage.setCurrent(storePOIPage.getCurrent());
        ipage.setPages(storePOIPage.getPages());
        ipage.setSize(storePOIPage.getSize());
        ipage.setTotal(storePOIPage.getTotal());
        ipage.setRecords(vos);
        return ipage;
    }

    /**
     * 获取当前管理类代理实例以保留增强逻辑
     */
    public VisualizationStoreManage proxy() {
        return CommonBeanFactory.getBean(this.getClass());
    }

    /**
     * 将收藏查询结果转换为前端展示对象
     */
    public List<VisualizationStoreVO> formatResult(List<StorePO> pos) {
        if (CollectionUtils.isEmpty(pos)) return new ArrayList<>();
        return pos.stream().map(po ->
                new VisualizationStoreVO(
                        po.getStoreId(), po.getResourceId(), po.getName(),
                        po.getType(), coreUserManage.getUserName(po.getCreator()), coreUserManage.getUserName(po.getEditor()),
                        po.getEditTime(), 9, po.getExtFlag(), po.getExtFlag1())).toList();
    }

    /**
     * 按筛选条件分页查询收藏资源原始记录
     */
    public IPage<StorePO> queryStorePage(int goPage, int pageSize, VisualizationWorkbranchQueryRequest request) {
        Long uid = AuthUtils.getUser().getUserId();
        QueryWrapper<Object> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("s.uid", uid);
        queryWrapper.isNotNull("s.resource_id");
        if (StringUtils.isNotBlank(request.getType())) {
            BusiResourceEnum busiResourceEnum = busiResourceType(request.getType());
            queryWrapper.eq("s.resource_type", busiResourceEnum.getFlag());
        }
        if (StringUtils.isNotBlank(request.getKeyword())) {
            queryWrapper.apply(dialect().caseInsensitiveLike("v.name", "{0}"), request.getKeyword());
        }
        String info = CommunityUtils.getInfo();
        if (StringUtils.isNotBlank(info)) {
            queryWrapper.notExists(String.format(info, "s.resource_id"));
        }
        queryWrapper.orderBy(true, request.isAsc(), "v.update_time");
        Page<StorePO> page = new Page<>(goPage, pageSize);
        return coreStoreExtMapper.query(page, queryWrapper);
    }

    /**
     * 将请求中的资源类型转换为业务资源枚举
     */
    private BusiResourceEnum busiResourceType(String type) {
        try {
            return BusiResourceEnum.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            CrestException.throwException("type is invalid");
            return null;
        }
    }

    /**
     * 收藏检索运行在系统元数据库上，关键字拼接语法必须跟随当前元库方言。
     */
    private MetadataDbDialect dialect() {
        return MetadataDbDialects.current(environment);
    }
}
