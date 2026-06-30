package io.crest.substitute.permissions.auth;

import io.crest.api.permissions.auth.api.AuthApi;
import io.crest.api.permissions.auth.dto.BusiPerEditor;
import io.crest.api.permissions.auth.dto.BusiPermissionRequest;
import io.crest.api.permissions.auth.dto.BusiTargetPerCreator;
import io.crest.api.permissions.auth.dto.MenuPerEditor;
import io.crest.api.permissions.auth.dto.MenuPermissionRequest;
import io.crest.api.permissions.auth.dto.MenuTargetPerCreator;
import io.crest.api.permissions.auth.vo.PermissionItem;
import io.crest.api.permissions.auth.vo.PermissionVO;
import io.crest.api.permissions.auth.vo.ResourceItemVO;
import io.crest.api.permissions.auth.vo.ResourceVO;
import io.crest.constant.LogOT;
import io.crest.constant.LogST;
import io.crest.log.CrestAudit;
import io.crest.metadata.MetadataDbDialect;
import io.crest.metadata.MetadataDbDialects;
import io.crest.utils.IDUtils;
import jakarta.annotation.Resource;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/auth")
// 提供资源权限和菜单权限的替代实现接口
public class SubstituleAuthServer implements AuthApi {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private PlatformPermissionManage platformPermissionManage;

    @Resource
    private Environment environment;

    // 查询指定业务类型的资源列表
    @Override
    public List<ResourceVO> busiResource(String flag) {
        String type = normalizeFlag(flag);
        return jdbcTemplate.query("""
                SELECT id, resource_id, name
                FROM core_iam_resource_index
                WHERE resource_type = ?
                ORDER BY update_time DESC, id DESC
                """, (rs, rowNum) -> {
            ResourceVO vo = new ResourceVO();
            vo.setId(Long.parseLong(rs.getString("resource_id")));
            vo.setName(rs.getString("name"));
            vo.setLeaf(true);
            return vo;
        }, type);
    }

    // 查询角色或用户在业务资源上的权限
    @Override
    public PermissionVO busiPermission(BusiPermissionRequest request) {
        PermissionVO vo = new PermissionVO();
        vo.setRoot(false);
        vo.setReadonly(false);
        String targetType = targetType(request.getType());
        vo.setPermissions(jdbcTemplate.query("""
                SELECT %s AS resource_id, permission
                FROM core_iam_resource_permission
                WHERE target_type = ? AND target_id = ? AND resource_type = ?
                """.formatted(dialect().numberCast("resource_id")), (rs, rowNum) -> {
            PermissionItem item = new PermissionItem();
            item.setId(rs.getLong("resource_id"));
            item.setWeight(platformPermissionManage.weightByPermission(rs.getString("permission")));
            return item;
        }, targetType, request.getId(), normalizeFlag(request.getFlag())));
        vo.setPermissionOrigins(List.of());
        return vo;
    }

    // 查询指定目标在业务资源上的权限
    @Override
    public PermissionVO busiTargetPermission(BusiPermissionRequest request) {
        return busiPermission(request);
    }

    // 查询菜单权限资源树
    @Override
    public List<ResourceVO> menuResource() {
        List<ResourceVO> menus = jdbcTemplate.query("""
                SELECT id, pid, name, type
                FROM core_iam_menu
                WHERE in_layout = 1
                ORDER BY menu_sort ASC, id ASC
                """, (rs, rowNum) -> {
            ResourceVO vo = new ResourceVO();
            vo.setId(rs.getLong("id"));
            vo.setName(rs.getString("name"));
            vo.setLeaf(rs.getInt("type") != 1);
            vo.setExtraFlag(rs.getInt("type"));
            return vo;
        });
        Map<Long, ResourceVO> byId = menus.stream().collect(Collectors.toMap(ResourceVO::getId, item -> item));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT id, pid FROM core_iam_menu WHERE in_layout = 1");
        for (Map<String, Object> row : rows) {
            Long id = ((Number) row.get("id")).longValue();
            Long pid = ((Number) row.get("pid")).longValue();
            if (pid != 0 && byId.containsKey(pid) && byId.containsKey(id)) {
                ResourceVO parent = byId.get(pid);
                if (parent.getChildren() == null) {
                    parent.setChildren(new ArrayList<>());
                }
                parent.getChildren().add(byId.get(id));
            }
        }
        return rows.stream()
                .filter(row -> ((Number) row.get("pid")).longValue() == 0L)
                .map(row -> byId.get(((Number) row.get("id")).longValue()))
                .toList();
    }

    // 查询角色菜单权限
    @Override
    public PermissionVO menuPermission(MenuPermissionRequest request) {
        PermissionVO vo = new PermissionVO();
        vo.setRoot(false);
        vo.setReadonly(false);
        vo.setPermissions(platformPermissionManage.roleMenuPermissions(request.getId()));
        vo.setPermissionOrigins(List.of());
        return vo;
    }

    // 查询指定目标的菜单权限
    @Override
    public PermissionVO menuTargetPermission(MenuPermissionRequest request) {
        return menuPermission(request);
    }

    // 保存业务资源权限配置
    @Override
    @CrestAudit(ot = LogOT.AUTHORIZE, st = LogST.DATA, id = "#p0.id")
    public void saveBusiPer(BusiPerEditor editor) {
        platformPermissionManage.requireSystemAdmin();
        String targetType = targetType(editor.getType());
        jdbcTemplate.update("""
                DELETE FROM core_iam_resource_permission
                WHERE target_type = ? AND target_id = ? AND resource_type = ?
                """, targetType, editor.getId(), normalizeFlag(editor.getFlag()));
        if (editor.getPermissions() == null) {
            return;
        }
        for (PermissionItem item : editor.getPermissions()) {
            saveResourcePermission(normalizeFlag(editor.getFlag()), String.valueOf(item.getId()), targetType,
                    editor.getId(), platformPermissionManage.permissionByWeight(item.getWeight()));
        }
    }

    // 批量保存业务资源目标权限配置
    @Override
    @CrestAudit(ot = LogOT.AUTHORIZE, st = LogST.DATA)
    public void saveBusiTargetPer(BusiTargetPerCreator creator) {
        platformPermissionManage.requireSystemAdmin();
        if (creator.getPermissions() == null || creator.getIds() == null) {
            return;
        }
        String targetType = targetType(creator.getType());
        for (Long resourceId : creator.getIds()) {
            jdbcTemplate.update("""
                    DELETE FROM core_iam_resource_permission
                    WHERE resource_type = ? AND resource_id = ? AND target_type = ?
                    """, normalizeFlag(creator.getFlag()), String.valueOf(resourceId), targetType);
            for (PermissionItem item : creator.getPermissions()) {
                saveResourcePermission(normalizeFlag(creator.getFlag()), String.valueOf(resourceId), targetType,
                        item.getId(), platformPermissionManage.permissionByWeight(item.getWeight()));
            }
        }
    }

    // 保存角色菜单权限配置
    @Override
    @CrestAudit(ot = LogOT.AUTHORIZE, st = LogST.MENU, id = "#p0.id")
    public void saveMenuPer(MenuPerEditor editor) {
        platformPermissionManage.requireSystemAdmin();
        jdbcTemplate.update("DELETE FROM core_iam_role_menu_permission WHERE rid = ?", editor.getId());
        if (editor.getPermissions() == null) {
            return;
        }
        for (PermissionItem item : editor.getPermissions()) {
            saveRoleMenuPermission(editor.getId(), item.getId(),
                    platformPermissionManage.permissionByWeight(item.getWeight()));
        }
    }

    // 批量保存菜单目标权限配置
    @Override
    @CrestAudit(ot = LogOT.AUTHORIZE, st = LogST.MENU)
    public void saveMenuTargetPer(MenuTargetPerCreator creator) {
        platformPermissionManage.requireSystemAdmin();
        if (creator.getPermissions() == null || creator.getIds() == null) {
            return;
        }
        for (Long roleId : creator.getIds()) {
            MenuPerEditor editor = new MenuPerEditor();
            editor.setId(roleId);
            editor.setPermissions(creator.getPermissions());
            saveMenuPer(editor);
        }
    }

    // 查询业务资源下已授权的全部目标
    @Override
    public List<ResourceItemVO> busiTargetPermissionAll(BusiPermissionRequest request) {
        String targetType = targetType(request.getType());
        if ("user".equals(targetType)) {
            return jdbcTemplate.query("""
                    SELECT DISTINCT u.id, u.account, u.name
                    FROM core_iam_user u
                    INNER JOIN core_iam_resource_permission p ON p.target_id = u.id AND p.target_type = 'user'
                    WHERE p.resource_type = ? AND p.resource_id = ?
                    ORDER BY u.name ASC, u.account ASC
                    """, (rs, rowNum) -> {
                ResourceItemVO vo = new ResourceItemVO();
                vo.setId(rs.getLong("id"));
                vo.setAccount(rs.getString("account"));
                vo.setName(rs.getString("name"));
                return vo;
            }, normalizeFlag(request.getFlag()), String.valueOf(request.getId()));
        }
        return jdbcTemplate.query("""
                SELECT DISTINCT r.id, r.code AS account, r.name
                FROM core_iam_role r
                INNER JOIN core_iam_resource_permission p ON p.target_id = r.id AND p.target_type = 'role'
                WHERE p.resource_type = ? AND p.resource_id = ?
                ORDER BY r.system_role DESC, r.name ASC
                """, (rs, rowNum) -> {
            ResourceItemVO vo = new ResourceItemVO();
            vo.setId(rs.getLong("id"));
            vo.setAccount(rs.getString("account"));
            vo.setName(rs.getString("name"));
            return vo;
        }, normalizeFlag(request.getFlag()), String.valueOf(request.getId()));
    }

    // 写入单条资源权限记录
    private void saveResourcePermission(String resourceType, String resourceId, String targetType, Long targetId, String permission) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM core_iam_resource_permission
                WHERE resource_type = ? AND resource_id = ? AND target_type = ? AND target_id = ? AND permission = ?
                """, Integer.class, resourceType, resourceId, targetType, targetId, permission);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO core_iam_resource_permission(id, resource_type, resource_id, target_type, target_id, permission, create_time)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, IDUtils.snowID(), resourceType, resourceId, targetType, targetId, permission, System.currentTimeMillis());
    }

    // 权限表存在唯一约束，先判重再插入可同时兼容 MySQL 和 OB Oracle。
    private void saveRoleMenuPermission(Long rid, Long menuId, String permission) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM core_iam_role_menu_permission
                WHERE rid = ? AND menu_id = ? AND permission = ?
                """, Integer.class, rid, menuId, permission);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO core_iam_role_menu_permission(id, rid, menu_id, permission, create_time)
                VALUES (?, ?, ?, ?, ?)
                """, IDUtils.snowID(), rid, menuId, permission, System.currentTimeMillis());
    }

    // 将前端目标类型转换为权限目标类型
    private String targetType(Integer type) {
        return type != null && type == 1 ? "user" : "role";
    }

    // 统一业务资源类型标识
    private String normalizeFlag(String flag) {
        if ("dataV".equalsIgnoreCase(flag)) {
            return "screen";
        }
        if ("dashboard".equalsIgnoreCase(flag)) {
            return "panel";
        }
        return flag == null ? "panel" : flag;
    }

    // 权限资源 SQL 片段需要复用元数据库方言，避免调用侧重复判断
    private MetadataDbDialect dialect() {
        return MetadataDbDialects.current(environment);
    }
}
