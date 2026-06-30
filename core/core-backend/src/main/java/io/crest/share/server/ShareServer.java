package io.crest.share.server;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.crest.api.visualization.request.VisualizationWorkbranchQueryRequest;
import io.crest.api.share.ShareApi;
import io.crest.api.share.request.*;
import io.crest.api.share.vo.ShareGridVO;
import io.crest.api.share.vo.ShareProxyVO;
import io.crest.api.share.vo.ShareVO;
import io.crest.utils.BeanUtils;
import io.crest.share.dao.auto.entity.CoreShare;
import io.crest.share.manage.ShareManage;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RequestMapping("/share")
@RestController
// 提供分享开关、访问校验和分享列表接口
public class ShareServer implements ShareApi {

    @Resource(name = "shareManage")
    private ShareManage shareManage;

    // 查询资源是否已开启分享
    @Override
    public boolean status(Long resourceId) {
        return ObjectUtils.isNotEmpty(shareManage.queryByResource(resourceId));
    }

    // 切换资源分享状态
    @Override
    public void switcher(Long resourceId) {
        shareManage.switcher(resourceId);
    }

    // 修改分享有效期
    @Override
    public void editExp(ShareExpRequest request) {
        shareManage.editExp(request.getResourceId(), request.getExp());
    }

    // 修改分享访问密码
    @Override
    public void editPwd(SharePwdRequest request) {
        shareManage.editPwd(request.getResourceId(), request.getPwd(), request.getAutoPwd());
    }

    // 查询资源分享详情
    @Override
    public ShareVO detail(Long resourceId) {
        CoreShare coreShare = shareManage.queryByResource(resourceId);
        if (ObjectUtils.isEmpty(coreShare)) return null;
        return BeanUtils.copyBean(new ShareVO(), coreShare);
    }

    // 查询分享资源列表
    @Override
    public List<ShareGridVO> query(VisualizationWorkbranchQueryRequest request) {
        return shareManage.query(1, 20, request).getRecords();
    }

    // 分页查询分享资源列表
    @Override
    public IPage<ShareGridVO> pager(int goPage, int pageSize, VisualizationWorkbranchQueryRequest request) {
        return shareManage.query(goPage, pageSize, request);
    }

    // 查询分享代理访问信息
    @Override
    public ShareProxyVO proxyInfo(ShareProxyRequest request) {
        return shareManage.proxyInfo(request);
    }

    // 校验分享访问密码
    @Override
    public boolean validatePwd(SharePwdValidator validator) {
        return shareManage.validatePwd(validator);
    }

    // 查询用户关联的分享资源
    @Override
    public Map<String, String> queryRelationByUserId(Long uid) {
        return shareManage.queryRelationByUserId(uid);
    }

    // 修改分享 UUID
    @Override
    public String editUuid(ShareUuidEditor editor) {
        return shareManage.editUuid(editor);
    }
}
