package io.crest.substitute.permissions.org;

import io.crest.api.permissions.org.api.OrgApi;
import io.crest.api.permissions.org.dto.OrgCreator;
import io.crest.api.permissions.org.dto.OrgEditor;
import io.crest.api.permissions.org.dto.OrgLazyRequest;
import io.crest.api.permissions.org.dto.OrgRequest;
import io.crest.api.permissions.org.vo.LazyMountedVO;
import io.crest.api.permissions.org.vo.LazyTreeVO;
import io.crest.api.permissions.org.vo.MountedVO;
import io.crest.api.permissions.org.vo.OrgDetailVO;
import io.crest.api.permissions.org.vo.OrgPageVO;
import io.crest.constant.LogOT;
import io.crest.constant.LogST;
import io.crest.log.CrestAudit;
import io.crest.model.KeywordRequest;
import io.crest.substitute.permissions.auth.PlatformPermissionManage;
import io.crest.utils.AuthUtils;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/org")
/**
 * 组织管理接口控制器，负责组织树、挂载组织和组织详情操作
 */
public class SubstituleOrgServer implements OrgApi {

    @Resource
    private CrestOrgManage crestOrgManage;

    @Resource
    private PlatformPermissionManage platformPermissionManage;

    /**
     * 查询组织分页树
     */
    @Override
    public List<OrgPageVO> pageTree(OrgRequest request) {
        platformPermissionManage.requireSystemAdmin();
        return crestOrgManage.pageTree(request);
    }

    /**
     * 懒加载查询组织树
     */
    @Override
    public LazyTreeVO lazyPageTree(OrgLazyRequest request) {
        platformPermissionManage.requireSystemAdmin();
        return crestOrgManage.lazyPageTree(request);
    }

    /**
     * 创建组织并记录审计日志
     */
    @Override
    @CrestAudit(ot = LogOT.CREATE, st = LogST.ORG)
    public Long create(OrgCreator creator) {
        return crestOrgManage.create(creator);
    }

    /**
     * 编辑组织并记录审计日志
     */
    @Override
    @CrestAudit(ot = LogOT.MODIFY, st = LogST.ORG, id = "#p0.id")
    public void edit(OrgEditor editor) {
        crestOrgManage.edit(editor);
    }

    /**
     * 删除组织并记录审计日志
     */
    @Override
    @CrestAudit(ot = LogOT.DELETE, st = LogST.ORG, id = "#p0")
    public void delete(Long id) {
        crestOrgManage.delete(id);
    }

    /**
     * 查询当前用户可挂载的组织列表
     */
    @Override
    public List<MountedVO> mounted(KeywordRequest request) {
        Long uid = AuthUtils.getUser() == null ? 1L : AuthUtils.getUser().getUserId();
        String keyword = request == null ? null : request.getKeyword();
        return platformPermissionManage.mountedOrgs(uid, keyword);
    }

    /**
     * 懒加载查询当前用户可挂载的组织树
     */
    @Override
    public LazyMountedVO lazyMounted(OrgLazyRequest request) {
        LazyMountedVO vo = new LazyMountedVO();
        vo.setNodes(mounted(request));
        vo.setName("组织");
        vo.setExpandKeyList(List.of(String.valueOf(PlatformPermissionManage.ROOT_ORG_ID)));
        return vo;
    }

    /**
     * 判断组织下是否存在资源
     */
    @Override
    public boolean resourceExist(Long oid) {
        return crestOrgManage.resourceExist(oid);
    }

    /**
     * 查询组织详情
     */
    @Override
    public OrgDetailVO detail(Long oid) {
        return crestOrgManage.detail(oid);
    }

    /**
     * 查询当前用户默认组织下的子组织
     */
    @Override
    public List<String> subOrgs() {
        Long oid = AuthUtils.getUser() == null ? PlatformPermissionManage.ROOT_ORG_ID : AuthUtils.getUser().getDefaultOid();
        return crestOrgManage.subOrgs(oid);
    }
}
