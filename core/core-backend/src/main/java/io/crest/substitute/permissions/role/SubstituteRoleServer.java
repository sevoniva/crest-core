package io.crest.substitute.permissions.role;

import io.crest.api.permissions.role.api.RoleApi;
import io.crest.api.permissions.role.dto.MountExternalUserRequest;
import io.crest.api.permissions.role.dto.MountUserRequest;
import io.crest.api.permissions.role.dto.RoleCopyRequest;
import io.crest.api.permissions.role.dto.RoleCreator;
import io.crest.api.permissions.role.dto.RoleEditor;
import io.crest.api.permissions.role.dto.RoleRequest;
import io.crest.api.permissions.role.dto.UnmountUserRequest;
import io.crest.api.permissions.role.vo.ExternalUserVO;
import io.crest.api.permissions.role.vo.RoleDetailVO;
import io.crest.api.permissions.role.vo.RoleVO;
import io.crest.constant.LogOT;
import io.crest.constant.LogST;
import io.crest.log.CrestAudit;
import io.crest.model.KeywordRequest;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/role")
// 提供角色管理 API 的替代实现入口
public class SubstituteRoleServer implements RoleApi {

    @Resource
    private CrestRoleManage crestRoleManage;

    // 查询角色列表
    @Override
    public List<RoleVO> query(KeywordRequest request) {
        return crestRoleManage.query(request == null ? null : request.getKeyword());
    }

    // 创建角色
    @Override
    @CrestAudit(ot = LogOT.CREATE, st = LogST.ROLE)
    public Long create(RoleCreator creator) {
        return crestRoleManage.create(creator);
    }

    // 编辑角色信息
    @Override
    @CrestAudit(ot = LogOT.MODIFY, st = LogST.ROLE, id = "#p0.id")
    public void edit(RoleEditor editor) {
        crestRoleManage.edit(editor);
    }

    // 绑定用户到角色
    @Override
    @CrestAudit(ot = LogOT.BIND, st = LogST.ROLE, id = "#p0.rid")
    public void mountUser(MountUserRequest request) {
        crestRoleManage.mountUsers(request.getRid(), request.getUids());
    }

    // 绑定外部用户到角色
    @Override
    @CrestAudit(ot = LogOT.BIND, st = LogST.ROLE, id = "#p0.rid")
    public void mountExternalUser(MountExternalUserRequest request) {
        crestRoleManage.mountUsers(request.getRid(), List.of(request.getUid()));
    }

    // 搜索外部用户
    @Override
    public ExternalUserVO searchExternalUser(String keyword) {
        return crestRoleManage.searchExternalUser(keyword);
    }

    // 解绑角色用户
    @Override
    @CrestAudit(ot = LogOT.UNBIND, st = LogST.ROLE, id = "#p0.rid")
    public void unMountUser(UnmountUserRequest request) {
        crestRoleManage.unmountUser(request.getRid(), request.getUid());
    }

    // 查询用户可选角色
    @Override
    public List<RoleVO> optionForUser(RoleRequest request) {
        return crestRoleManage.query(request == null ? null : request.getKeyword());
    }

    // 查询用户已选角色
    @Override
    public List<RoleVO> selectedForUser(RoleRequest request) {
        if (request == null || request.getUid() == null) {
            return List.of();
        }
        return crestRoleManage.selectedForUser(request.getUid(), request.getKeyword());
    }

    // 查询角色详情
    @Override
    public RoleDetailVO detail(Long rid) {
        return crestRoleManage.detail(rid);
    }

    // 删除角色
    @Override
    @CrestAudit(ot = LogOT.DELETE, st = LogST.ROLE, id = "#p0")
    public void delete(Long rid) {
        crestRoleManage.delete(rid);
    }

    // 查询解绑用户前的影响数量
    @Override
    public Integer beforeUnmountInfo(UnmountUserRequest request) {
        return crestRoleManage.beforeUnmountInfo(request.getRid(), request.getUid());
    }

    // 复制角色基础信息
    @Override
    @CrestAudit(ot = LogOT.CREATE, st = LogST.ROLE, id = "#p0.copyId")
    public void copy(RoleCopyRequest request) {
        RoleDetailVO detail = crestRoleManage.detail(request.getCopyId());
        RoleCreator creator = new RoleCreator();
        creator.setName(request.getName() == null ? detail.getName() + " 副本" : request.getName());
        creator.setDesc(request.getDesc() == null ? detail.getDesc() : request.getDesc());
        creator.setTypeCode(detail.getTypeCode());
        crestRoleManage.create(creator);
    }

    // 查询当前组织下的角色
    @Override
    public List<RoleVO> byCurOrg(KeywordRequest request) {
        return query(request);
    }

    // 按组织 ID 查询角色
    @Override
    public List<RoleVO> queryWithOid(Long oid) {
        return crestRoleManage.queryByOid(oid, null);
    }
}
