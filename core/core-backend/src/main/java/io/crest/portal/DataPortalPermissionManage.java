package io.crest.portal;

import io.crest.metadata.MetadataDbDialect;
import io.crest.metadata.MetadataDbDialects;
import io.crest.substitute.permissions.auth.PlatformPermissionManage;
import io.crest.substitute.permissions.user.CrestUserManage;
import io.crest.utils.AuthUtils;
import jakarta.annotation.Resource;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
/**
 * 数据门户发布资源的读权限判断组件
 */
public class DataPortalPermissionManage {

    /**
     * 查询发布资源和权限数据的 JDBC 模板
     */
    @Resource
    private JdbcTemplate jdbcTemplate;

    /**
     * 平台权限管理器，用于读取组织和角色范围
     */
    @Resource
    private PlatformPermissionManage platformPermissionManage;

    /**
     * 用户管理器，用于判断管理员身份
     */
    @Resource
    private CrestUserManage crestUserManage;

    @Resource
    private Environment environment;

    /**
     * 返回当前登录用户 ID
     */
    public Long currentUid() {
        return AuthUtils.getUser() == null ? null : AuthUtils.getUser().getUserId();
    }

    /**
     * 判断用户是否拥有后台访问权限
     */
    public boolean hasBackendAccess(Long uid) {
        return uid != null && (crestUserManage.isAdmin(uid) || platformPermissionManage.isSystemAdmin(uid));
    }

    /**
     * 判断当前用户是否可读取指定已发布可视化资源
     */
    public boolean canReadPublishedVisualization(Long id, String type) {
        if (id == null) {
            return false;
        }
        Long uid = currentUid();
        if (uid == null || hasBackendAccess(uid)) {
            return true;
        }
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(1)
                FROM core_visualization v
                WHERE v.id = ?
                  AND v.delete_flag = 0
                  AND v.node_type = 'leaf'
                  AND v.status = 1
                """);
        List<Object> args = new ArrayList<>();
        args.add(id);
        if (type != null) {
            sql.append(" AND v.type = ?");
            args.add(type);
        }
        appendReadScope(sql, args, uid);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count != null && count > 0;
    }

    /**
     * 判断已发布可视化是否引用指定数据集
     */
    public boolean canReadPublishedVisualizationDataset(Long id, Long datasetGroupId) {
        if (id == null || datasetGroupId == null || !canReadPublishedVisualization(id, null)) {
            return false;
        }
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM core_chart_view c
                WHERE c.scene_id = ?
                  AND c.table_id = ?
                """, Long.class, id, datasetGroupId);
        return count != null && count > 0;
    }

    /**
     * 为已发布可视化查询追加当前用户的可读权限范围
     */
    public void appendReadScope(StringBuilder sql, List<Object> args, Long uid) {
        if (uid == null || hasBackendAccess(uid)) {
            return;
        }
        Long oid = platformPermissionManage.defaultOrgId(uid);
        List<Long> roleIds = platformPermissionManage.roleIds(uid, oid);
        roleIds = roleIds == null ? List.of() : roleIds;
        sql.append("""
                AND (
                  v.create_by = ?
                  OR v.org_id = ?
                  OR EXISTS (
                    SELECT 1 FROM core_iam_resource_permission p
                    WHERE %s = v.id
                      AND (
                        (v.type = 'dataV' AND p.resource_type = 'screen')
                        OR (v.type = 'dashboard' AND p.resource_type = 'panel')
                      )
                      AND p.permission IN ('read', 'manage')
                      AND (
                        (p.target_type = 'user' AND p.target_id = ?)
                        OR (p.target_type = 'org' AND p.target_id = ?)
                """.formatted(dialect().numberCast("p.resource_id")));
        args.add(String.valueOf(uid));
        args.add(oid);
        args.add(uid);
        args.add(oid);
        if (!roleIds.isEmpty()) {
            sql.append(" OR (p.target_type = 'role' AND p.target_id IN (");
            sql.append("?,".repeat(roleIds.size()));
            sql.setLength(sql.length() - 1);
            sql.append("))");
            args.addAll(roleIds);
        }
        sql.append("""
                      )
                  )
                )
                """);
    }

    // 门户权限 SQL 需要按元数据库方言处理数值转换
    private MetadataDbDialect dialect() {
        return MetadataDbDialects.current(environment);
    }
}
