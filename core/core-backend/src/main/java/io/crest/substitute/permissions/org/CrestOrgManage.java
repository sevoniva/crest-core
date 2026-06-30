package io.crest.substitute.permissions.org;

import io.crest.api.permissions.org.dto.OrgCreator;
import io.crest.api.permissions.org.dto.OrgEditor;
import io.crest.api.permissions.org.dto.OrgLazyRequest;
import io.crest.api.permissions.org.dto.OrgRequest;
import io.crest.api.permissions.org.vo.LazyOrgTreeNode;
import io.crest.api.permissions.org.vo.LazyTreeVO;
import io.crest.api.permissions.org.vo.OrgDetailVO;
import io.crest.api.permissions.org.vo.OrgPageVO;
import io.crest.exception.CrestException;
import io.crest.metadata.MetadataDbDialect;
import io.crest.metadata.MetadataDbDialects;
import io.crest.substitute.permissions.auth.PlatformPermissionManage;
import io.crest.utils.IDUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
/**
 * 组织数据管理组件，负责组织树查询和组织增删改
 */
public class CrestOrgManage {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private PlatformPermissionManage platformPermissionManage;

    @Resource
    private Environment environment;

    /**
     * 查询完整组织树
     */
    public List<OrgPageVO> pageTree(OrgRequest request) {
        List<OrgPageVO> orgs = jdbcTemplate.query("""
                SELECT id, pid, name, create_time, readonly
                FROM core_iam_org
                WHERE enable = 1
                ORDER BY sort ASC, id ASC
                """, (rs, rowNum) -> {
            OrgPageVO vo = new OrgPageVO();
            vo.setId(rs.getLong("id"));
            vo.setPid(rs.getLong("pid"));
            vo.setName(rs.getString("name"));
            vo.setCreateTime(rs.getLong("create_time"));
            vo.setReadOnly(rs.getBoolean("readonly"));
            return vo;
        });
        Map<Long, OrgPageVO> byId = orgs.stream().collect(Collectors.toMap(OrgPageVO::getId, item -> item));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT id, pid FROM core_iam_org WHERE enable = 1");
        for (Map<String, Object> row : rows) {
            Long id = ((Number) row.get("id")).longValue();
            Number pidNumber = (Number) row.get("pid");
            Long pid = pidNumber == null ? 0L : pidNumber.longValue();
            if (pid != 0 && byId.containsKey(pid) && byId.containsKey(id)) {
                OrgPageVO parent = byId.get(pid);
                if (parent.getChildren() == null) {
                    parent.setChildren(new java.util.ArrayList<>());
                }
                parent.getChildren().add(byId.get(id));
            }
        }
        return rows.stream()
                .filter(row -> ((Number) row.get("pid")).longValue() == 0L)
                .map(row -> byId.get(((Number) row.get("id")).longValue()))
                .toList();
    }

    /**
     * 懒加载查询指定父组织下的组织树节点
     */
    public LazyTreeVO lazyPageTree(OrgLazyRequest request) {
        Long pid = request == null || request.getPid() == null ? 0L : request.getPid();
        LazyTreeVO vo = new LazyTreeVO();
        vo.setNodes(children(pid));
        vo.setExpandKeyList(List.of(String.valueOf(PlatformPermissionManage.ROOT_ORG_ID)));
        return vo;
    }

    /**
     * 创建组织
     */
    @Transactional
    public Long create(OrgCreator creator) {
        platformPermissionManage.requireSystemAdmin();
        if (creator == null || StringUtils.isBlank(creator.getName())) {
            CrestException.throwException("组织名称不能为空");
        }
        Long pid = creator.getPid() == null ? 0L : creator.getPid();
        String parentPath = "/";
        int level = 0;
        if (pid != 0L) {
            OrgDetailVO parent = detail(pid);
            if (parent == null) {
                CrestException.throwException("上级组织不存在");
            }
            parentPath = parent.getRootPath();
            level = parentPath.split("/").length - 2;
        }
        long id = creator.getId() == null ? IDUtils.snowID() : creator.getId();
        long now = System.currentTimeMillis();
        jdbcTemplate.update("""
                INSERT INTO core_iam_org(id, pid, name, code, path, level, sort, enable, readonly, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, pid, creator.getName().trim(), null, parentPath + id + "/", level, 0, true, false, now, now);
        return id;
    }

    /**
     * 编辑组织名称
     */
    @Transactional
    public void edit(OrgEditor editor) {
        platformPermissionManage.requireSystemAdmin();
        if (editor == null || editor.getId() == null || StringUtils.isBlank(editor.getName())) {
            CrestException.throwException("组织名称不能为空");
        }
        jdbcTemplate.update("UPDATE core_iam_org SET name = ?, update_time = ? WHERE id = ? AND readonly = 0",
                editor.getName().trim(), System.currentTimeMillis(), editor.getId());
    }

    /**
     * 删除空组织
     */
    @Transactional
    public void delete(Long id) {
        platformPermissionManage.requireSystemAdmin();
        if (id == null || id == PlatformPermissionManage.ROOT_ORG_ID) {
            CrestException.throwException("默认组织不能删除");
        }
        if (resourceExist(id)) {
            CrestException.throwException("组织下存在用户、子组织或资源，不能删除");
        }
        jdbcTemplate.update("DELETE FROM core_iam_org WHERE id = ? AND readonly = 0", id);
    }

    /**
     * 判断组织下是否存在子组织、用户或资源
     */
    public boolean resourceExist(Long oid) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT
                  (SELECT COUNT(1) FROM core_iam_org WHERE pid = ?) +
                  (SELECT COUNT(1) FROM core_iam_user_org WHERE oid = ?) +
                  (SELECT COUNT(1) FROM core_iam_resource_index WHERE oid = ?)
                """, Integer.class, oid, oid, oid);
        return count != null && count > 0;
    }

    /**
     * 查询组织详情
     */
    public OrgDetailVO detail(Long oid) {
        List<OrgDetailVO> list = jdbcTemplate.query("""
                SELECT id, name, pid, path FROM core_iam_org WHERE id = ?
                """.transform(this::limitOne), (rs, rowNum) -> new OrgDetailVO(rs.getLong("id"), rs.getString("name"),
                rs.getLong("pid"), rs.getString("path")), oid);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 查询指定组织及其下级组织 ID
     */
    public List<String> subOrgs(Long oid) {
        OrgDetailVO org = detail(oid);
        if (org == null) {
            return List.of();
        }
        return jdbcTemplate.queryForList("SELECT " + dialect().stringCast("id") + " FROM core_iam_org WHERE path LIKE ?",
                String.class, org.getRootPath() + "%");
    }

    // 组织详情接口只需要一条记录，分页语法交给元数据库方言保持 MySQL/OB Oracle 兼容。
    private String limitOne(String sql) {
        return dialect().limitOne(sql.stripTrailing());
    }

    // 组织管理 SQL 统一通过元数据库方言生成分页片段
    private MetadataDbDialect dialect() {
        return MetadataDbDialects.current(environment);
    }

    /**
     * 查询指定父组织下的懒加载节点
     */
    private List<LazyOrgTreeNode> children(Long pid) {
        return jdbcTemplate.query("""
                SELECT o.id, o.pid, o.name, o.create_time, o.readonly,
                       EXISTS(SELECT 1 FROM core_iam_org c WHERE c.pid = o.id) AS has_children
                FROM core_iam_org o
                WHERE o.pid = ? AND o.enable = 1
                ORDER BY o.sort ASC, o.id ASC
                """, (rs, rowNum) -> {
            LazyOrgTreeNode node = new LazyOrgTreeNode();
            node.setId(rs.getLong("id"));
            node.setPid(rs.getLong("pid"));
            node.setName(rs.getString("name"));
            node.setCreateTime(rs.getLong("create_time"));
            node.setReadOnly(rs.getBoolean("readonly"));
            node.setHasChildren(rs.getBoolean("has_children"));
            return node;
        }, pid);
    }
}
