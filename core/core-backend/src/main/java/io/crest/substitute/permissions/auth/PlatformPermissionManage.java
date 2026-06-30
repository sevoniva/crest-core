package io.crest.substitute.permissions.auth;

import io.crest.api.permissions.auth.vo.PermissionItem;
import io.crest.api.permissions.org.vo.MountedVO;
import io.crest.api.permissions.role.vo.RoleVO;
import io.crest.api.permissions.user.vo.UserGridRoleItem;
import io.crest.exception.CrestException;
import io.crest.metadata.MetadataDbDialects;
import io.crest.metadata.MetadataDbType;
import io.crest.utils.AuthUtils;
import io.crest.utils.IDUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

// 平台权限管理服务，集中处理组织、角色、资源权限和菜单权限的持久化规则
@Component
public class PlatformPermissionManage {

    public static final long ROOT_ORG_ID = 1L;
    public static final long SYSTEM_ADMIN_ROLE_ID = 1L;
    public static final long MEMBER_ROLE_ID = 2L;
    public static final long AUDITOR_ROLE_ID = 3L;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private Environment environment;

    // 查询用户默认组织，没有绑定时自动补齐根组织和普通用户角色
    public Long defaultOrgId(Long uid) {
        if (uid == null) {
            return ROOT_ORG_ID;
        }
        List<Long> orgIds = jdbcTemplate.queryForList("""
                SELECT oid FROM core_iam_user_org
                WHERE %s = ?
                ORDER BY default_org DESC, create_time ASC
                """.formatted(uidColumn()).transform(this::limitOne), Long.class, uid);
        if (!orgIds.isEmpty()) {
            return orgIds.get(0);
        }
        bindUserToOrg(uid, ROOT_ORG_ID, true);
        bindUserToRole(uid, ROOT_ORG_ID, MEMBER_ROLE_ID);
        return ROOT_ORG_ID;
    }

    // 查询用户在指定组织下拥有的角色 ID
    public List<Long> roleIds(Long uid, Long oid) {
        if (uid == null) {
            return List.of();
        }
        return jdbcTemplate.queryForList("""
                SELECT rid FROM core_iam_user_role
                WHERE %s = ? AND oid = ?
                ORDER BY rid ASC
                """.formatted(uidColumn()), Long.class, uid, oid == null ? defaultOrgId(uid) : oid);
    }

    // 查询用户角色列表，未配置角色时返回普通用户兜底角色
    public List<UserGridRoleItem> userRoleItems(Long uid) {
        List<UserGridRoleItem> roles = jdbcTemplate.query("""
                SELECT r.id, r.name
                FROM core_iam_role r
                INNER JOIN core_iam_user_role ur ON ur.rid = r.id
                WHERE ur.%s = ?
                ORDER BY r.system_role DESC, r.id ASC
                """.formatted(uidColumn()), (rs, rowNum) -> {
            UserGridRoleItem item = new UserGridRoleItem();
            item.setId(rs.getLong("id"));
            item.setName(rs.getString("name"));
            return item;
        }, uid);
        return roles.isEmpty() ? defaultMemberRoleItem() : roles;
    }

    // 查询用户角色 ID 字符串列表，供前端表单直接回显
    public List<String> userRoleIdStrings(Long uid) {
        return userRoleItems(uid).stream().map(item -> String.valueOf(item.getId())).toList();
    }

    // 判断用户是否拥有系统管理员权限
    public boolean isSystemAdmin(Long uid) {
        if (uid == null) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) FROM core_iam_user_role
                WHERE %s = ? AND rid = ?
                """.formatted(uidColumn()), Integer.class, uid, SYSTEM_ADMIN_ROLE_ID);
        return (count != null && count > 0) || AuthUtils.isSysAdmin(uid);
    }

    // 判断用户是否为指定组织管理员，系统管理员自动拥有组织管理权
    public boolean isOrgAdmin(Long uid, Long oid) {
        if (isSystemAdmin(uid)) {
            return true;
        }
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM core_iam_user_role ur
                INNER JOIN core_iam_role r ON r.id = ur.rid
                WHERE ur.%s = ? AND ur.oid = ? AND r.org_admin = 1
                """.formatted(uidColumn()), Integer.class, uid, oid == null ? defaultOrgId(uid) : oid);
        return count != null && count > 0;
    }

    // 替换用户默认组织下的全部角色
    @Transactional
    public void replaceUserRoles(Long uid, List<Long> roleIds) {
        replaceUserRoles(uid, defaultOrgId(uid), roleIds);
    }

    // 替换用户在指定组织下的全部角色，空角色列表使用普通用户角色兜底
    @Transactional
    public void replaceUserRoles(Long uid, Long oid, List<Long> roleIds) {
        oid = oid == null ? defaultOrgId(uid) : oid;
        jdbcTemplate.update("DELETE FROM core_iam_user_role WHERE " + uidColumn() + " = ? AND oid = ?", uid, oid);
        List<Long> effectiveRoleIds = roleIds == null || roleIds.isEmpty() ? List.of(MEMBER_ROLE_ID) : roleIds;
        for (Long roleId : effectiveRoleIds) {
            bindUserToRole(uid, oid, roleId);
        }
    }

    // 替换用户默认组织
    @Transactional
    public void replaceUserDefaultOrg(Long uid, Long oid) {
        bindUserToOrg(uid, oid == null ? ROOT_ORG_ID : oid, true);
    }

    // 查询组织名称
    public String orgName(Long oid) {
        List<String> names = jdbcTemplate.queryForList(limitOne("SELECT name FROM core_iam_org WHERE id = ?"),
                String.class, oid == null ? ROOT_ORG_ID : oid);
        return names.isEmpty() ? null : names.get(0);
    }

    // 单条查询交给元数据库方言生成，兼容 MySQL LIMIT 和 OB Oracle FETCH
    private String limitOne(String sql) {
        return MetadataDbDialects.current(environment).limitOffset(sql.stripTrailing(), 1, 0);
    }

    // 将用户绑定到组织，并在需要时设置为默认组织
    @Transactional
    public void bindUserToOrg(Long uid, Long oid, boolean defaultOrg) {
        long now = System.currentTimeMillis();
        if (defaultOrg) {
            jdbcTemplate.update("UPDATE core_iam_user_org SET default_org = 0 WHERE " + uidColumn() + " = ?", uid);
        }
        if (isObOracle()) {
            insertUserOrgIfMissing(uid, oid, defaultOrg, now);
        } else {
            jdbcTemplate.update("""
                    INSERT IGNORE INTO core_iam_user_org(id, uid, oid, default_org, create_time)
                    VALUES (?, ?, ?, ?, ?)
                    """, IDUtils.snowID(), uid, oid, defaultOrg, now);
        }
        if (defaultOrg) {
            jdbcTemplate.update("UPDATE core_iam_user_org SET default_org = 1 WHERE " + uidColumn() + " = ? AND oid = ?", uid, oid);
        }
    }

    // 将用户绑定到组织角色，空入参直接忽略
    @Transactional
    public void bindUserToRole(Long uid, Long oid, Long rid) {
        if (uid == null || oid == null || rid == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (isObOracle()) {
            insertUserRoleIfMissing(uid, oid, rid, now);
            return;
        }
        jdbcTemplate.update("""
                INSERT IGNORE INTO core_iam_user_role(id, uid, oid, rid, create_time)
                VALUES (?, ?, ?, ?, ?)
                """, IDUtils.snowID(), uid, oid, rid, now);
    }

    // 查询指定组织下的角色列表，可按名称模糊过滤
    public List<RoleVO> roles(Long oid, String keyword) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT id, name, readonly, system_role FROM core_iam_role WHERE oid = ?");
        args.add(oid == null ? ROOT_ORG_ID : oid);
        if (StringUtils.isNotBlank(keyword)) {
            sql.append(" AND name LIKE ?");
            args.add("%" + keyword.trim() + "%");
        }
        sql.append(" ORDER BY system_role DESC, id ASC");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            RoleVO vo = new RoleVO();
            vo.setId(rs.getLong("id"));
            vo.setName(rs.getString("name"));
            vo.setReadonly(rs.getBoolean("readonly"));
            vo.setRoot(rs.getBoolean("system_role"));
            return vo;
        }, args.toArray());
    }

    // 查询用户挂载的可访问组织，系统管理员可访问全部启用组织
    public List<MountedVO> mountedOrgs(Long uid, String keyword) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT DISTINCT o.id, o.name, o.readonly
                FROM core_iam_org o
                INNER JOIN core_iam_user_org uo ON uo.oid = o.id
                WHERE uo.%s = ? AND o.enable = 1
                """.formatted(uidColumn()));
        args.add(uid);
        if (isSystemAdmin(uid)) {
            sql = new StringBuilder("SELECT id, name, readonly FROM core_iam_org WHERE enable = 1");
            args.clear();
        }
        if (StringUtils.isNotBlank(keyword)) {
            sql.append(" AND name LIKE ?");
            args.add("%" + keyword.trim() + "%");
        }
        sql.append(" ORDER BY id ASC");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            MountedVO vo = new MountedVO();
            vo.setId(rs.getLong("id"));
            vo.setName(rs.getString("name"));
            vo.setReadOnly(rs.getBoolean("readonly"));
            return vo;
        }, args.toArray());
    }

    // 生成资源数据范围 SQL 片段，限制普通用户只能读取有权限的资源
    public String resourceScopeSql(String resourceType, String resourceIdColumn, String creatorColumn, String orgColumn) {
        Long uid = AuthUtils.getUser() == null ? null : AuthUtils.getUser().getUserId();
        Long oid = AuthUtils.getUser() == null ? ROOT_ORG_ID : AuthUtils.getUser().getDefaultOid();
        if (uid == null || isSystemAdmin(uid)) {
            return null;
        }
        String uidValue = String.valueOf(uid);
        String oidValue = String.valueOf(oid == null ? defaultOrgId(uid) : oid);
        String roleIds = roleIds(uid, oid).stream().map(String::valueOf).collect(Collectors.joining(","));
        if (StringUtils.isBlank(roleIds)) {
            roleIds = "-1";
        }
        StringBuilder sql = new StringBuilder();
        sql.append("(").append(creatorColumn).append(" = '").append(uidValue).append("'");
        if (StringUtils.isNotBlank(orgColumn)) {
            sql.append(" OR ").append(orgColumn).append(" = '").append(oidValue).append("'");
        } else {
            sql.append(" OR EXISTS (SELECT 1 FROM core_iam_resource_index cri WHERE cri.resource_type = '")
                    .append(resourceType).append("' AND cri.resource_id = ")
                    .append(MetadataDbDialects.current(environment).stringCast(resourceIdColumn))
                    .append(" AND cri.oid = ").append(oidValue).append(")");
        }
        sql.append(" OR EXISTS (SELECT 1 FROM core_iam_resource_permission crp WHERE crp.resource_type = '")
                .append(resourceType).append("' AND crp.resource_id = ")
                .append(MetadataDbDialects.current(environment).stringCast(resourceIdColumn))
                .append(" AND crp.permission IN ('read', 'manage') AND ((crp.target_type = 'user' AND crp.target_id = ")
                .append(uidValue).append(") OR (crp.target_type = 'role' AND crp.target_id IN (").append(roleIds)
                .append(")) OR (crp.target_type = 'org' AND crp.target_id = ").append(oidValue).append(")))");
        sql.append(")");
        return sql.toString();
    }

    // 计算用户对资源的权重，9 为所有者或系统管理员，7 为管理，1 为只读
    public int resourceWeight(String resourceType, String resourceId, String creator, Long resourceOrgId) {
        Long uid = AuthUtils.getUser() == null ? null : AuthUtils.getUser().getUserId();
        if (uid == null) {
            return 9;
        }
        Long oid = AuthUtils.getUser().getDefaultOid();
        oid = oid == null ? defaultOrgId(uid) : oid;
        if (isSystemAdmin(uid) || Objects.equals(String.valueOf(uid), StringUtils.trimToEmpty(creator))) {
            return 9;
        }
        if (resourceOrgId != null && isOrgAdmin(uid, resourceOrgId)) {
            return 7;
        }
        Set<String> permissions = resourcePermissions(resourceType, resourceId, uid, oid);
        if (permissions.contains("manage")) {
            return 7;
        }
        if (permissions.contains("read") || Objects.equals(resourceOrgId, oid)) {
            return 1;
        }
        return 1;
    }

    // 查询用户、组织和角色维度授予的资源权限集合
    private Set<String> resourcePermissions(String resourceType, String resourceId, Long uid, Long oid) {
        if (StringUtils.isBlank(resourceType) || StringUtils.isBlank(resourceId) || uid == null) {
            return Set.of();
        }
        List<Long> roleIds = roleIds(uid, oid);
        List<Object> args = new ArrayList<>();
        args.add(resourceType);
        args.add(resourceId);
        args.add(uid);
        args.add(oid);
        StringBuilder sql = new StringBuilder("""
                SELECT DISTINCT permission FROM core_iam_resource_permission
                WHERE resource_type = ? AND resource_id = ? AND permission IN ('read', 'manage')
                  AND ((target_type = 'user' AND target_id = ?)
                    OR (target_type = 'org' AND target_id = ?)
                """);
        if (!roleIds.isEmpty()) {
            sql.append(" OR (target_type = 'role' AND target_id IN (");
            sql.append(roleIds.stream().map(item -> "?").collect(Collectors.joining(",")));
            sql.append("))");
            args.addAll(roleIds);
        }
        sql.append(")");
        return jdbcTemplate.queryForList(sql.toString(), String.class, args.toArray()).stream()
                .map(StringUtils::lowerCase)
                .collect(Collectors.toSet());
    }

    // 将资源权重转换为权限集合
    public Set<String> permissionsFromWeight(int weight) {
        if (weight >= 7) {
            return Set.of("read", "manage");
        }
        if (weight > 0) {
            return Set.of("read");
        }
        return Set.of();
    }

    // 新增或更新资源索引，用于后续资源范围过滤和授权查询
    @Transactional
    public void upsertResource(String resourceType, String resourceId, Long oid, Long creator, String name, Long createTime, Long updateTime) {
        if (resourceType == null || resourceId == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long effectiveOid = oid == null ? ROOT_ORG_ID : oid;
        Long effectiveCreateTime = createTime == null ? now : createTime;
        Long effectiveUpdateTime = updateTime == null ? now : updateTime;
        if (isObOracle()) {
            jdbcTemplate.update("""
                    MERGE INTO core_iam_resource_index target
                    USING (SELECT ? AS resource_id, ? AS resource_type FROM DUAL) source
                    ON (target.resource_id = source.resource_id AND target.resource_type = source.resource_type)
                    WHEN MATCHED THEN UPDATE SET
                        target.oid = ?,
                        target.creator = ?,
                        target.name = ?,
                        target.update_time = ?
                    WHEN NOT MATCHED THEN INSERT
                        (id, resource_id, resource_type, oid, creator, name, create_time, update_time)
                        VALUES (?, source.resource_id, source.resource_type, ?, ?, ?, ?, ?)
                    """, resourceId, resourceType, effectiveOid, creator, name, effectiveUpdateTime,
                    IDUtils.snowID(), effectiveOid, creator, name, effectiveCreateTime, effectiveUpdateTime);
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO core_iam_resource_index(id, resource_id, resource_type, oid, creator, name, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE oid = VALUES(oid), creator = VALUES(creator), name = VALUES(name), update_time = VALUES(update_time)
                """, IDUtils.snowID(), resourceId, resourceType, effectiveOid, creator,
                name, effectiveCreateTime, effectiveUpdateTime);
    }

    // 删除资源索引及其显式授权记录
    @Transactional
    public void deleteResource(String resourceType, String resourceId) {
        jdbcTemplate.update("DELETE FROM core_iam_resource_index WHERE resource_type = ? AND resource_id = ?", resourceType, resourceId);
        jdbcTemplate.update("DELETE FROM core_iam_resource_permission WHERE resource_type = ? AND resource_id = ?", resourceType, resourceId);
    }

    // 将资源权重转换为单个最高权限
    public String permissionByWeight(int weight) {
        return weight >= 7 ? "manage" : "read";
    }

    // 将权限字符串转换为资源权重
    public int weightByPermission(String permission) {
        return "manage".equalsIgnoreCase(permission) ? 7 : 1;
    }

    // 要求当前用户必须具备系统管理员权限
    public void requireSystemAdmin() {
        Long uid = AuthUtils.getUser() == null ? null : AuthUtils.getUser().getUserId();
        if (!isSystemAdmin(uid)) {
            CrestException.throwException("当前用户没有系统管理权限");
        }
    }

    // 查询角色菜单权限，并转换为前端使用的权限权重
    public List<PermissionItem> roleMenuPermissions(Long rid) {
        return jdbcTemplate.query("""
                SELECT menu_id, MAX(CASE WHEN permission = 'manage' THEN 7 ELSE 1 END) AS weight
                FROM core_iam_role_menu_permission
                WHERE rid = ?
                GROUP BY menu_id
                """, (rs, rowNum) -> {
            PermissionItem item = new PermissionItem();
            item.setId(rs.getLong("menu_id"));
            item.setWeight(rs.getInt("weight"));
            return item;
        }, rid);
    }

    // 构造普通用户兜底角色项
    private List<UserGridRoleItem> defaultMemberRoleItem() {
        UserGridRoleItem role = new UserGridRoleItem();
        role.setId(MEMBER_ROLE_ID);
        role.setName("普通用户");
        return List.of(role);
    }

    // OB Oracle 不支持 INSERT IGNORE，先检查再补写用户组织绑定
    private void insertUserOrgIfMissing(Long uid, Long oid, boolean defaultOrg, long now) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) FROM core_iam_user_org
                WHERE %s = ? AND oid = ?
                """.formatted(uidColumn()), Integer.class, uid, oid);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO core_iam_user_org(id, %s, oid, default_org, create_time)
                VALUES (?, ?, ?, ?, ?)
                """.formatted(uidColumn()), IDUtils.snowID(), uid, oid, defaultOrg, now);
    }

    // OB Oracle 不支持 INSERT IGNORE，先检查再补写用户角色绑定
    private void insertUserRoleIfMissing(Long uid, Long oid, Long rid, long now) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) FROM core_iam_user_role
                WHERE %s = ? AND oid = ? AND rid = ?
                """.formatted(uidColumn()), Integer.class, uid, oid, rid);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO core_iam_user_role(id, %s, oid, rid, create_time)
                VALUES (?, ?, ?, ?, ?)
                """.formatted(uidColumn()), IDUtils.snowID(), uid, oid, rid, now);
    }

    // 用户绑定表的 UID 在 OB Oracle 中需要引用，MySQL 保持原字段写法
    private String uidColumn() {
        return isObOracle() ? MetadataDbDialects.current(environment).quoteIdentifier("UID") : "uid";
    }

    // 判断当前元数据库是否为 OB Oracle，集中收敛兼容分支
    private boolean isObOracle() {
        return MetadataDbDialects.current(environment).type() == MetadataDbType.OB_ORACLE;
    }
}
