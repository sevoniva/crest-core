package io.crest.operation.manage;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.crest.commons.constants.OptConstants;
import io.crest.metadata.MetadataDbDialects;
import io.crest.metadata.MetadataDbType;
import io.crest.operation.dao.auto.entity.CoreOptRecent;
import io.crest.operation.dao.auto.mapper.CoreOptRecentMapper;
import io.crest.utils.AuthUtils;
import io.crest.utils.IDUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Component
/**
 * 最近操作记录管理器
 */
public class CoreOptRecentManage {

    /**
     * 最近操作记录数据访问对象
     */
    @Autowired
    private CoreOptRecentMapper coreStoreMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Environment environment;

    /**
     * 按资源 ID 保存最近操作
     */
    public void saveOpt(Long resourceId, int resourceType, int optType) {
        saveOpt(resourceId, null, resourceType, optType);
    }

    /**
     * 按资源名称保存最近操作
     */
    public void saveOpt(String resourceName, int resourceType, int optType) {
        saveOpt(null, resourceName, resourceType, optType);
    }

    /**
     * 保存或更新当前用户的最近操作记录
     */
    public void saveOpt(Long resourceId, String resourceName, int resourceType, int optType) {
        if (AuthUtils.getUser() == null) {
            return;
        }
        Long uid = AuthUtils.getUser().getUserId();
        if (isObOracle()) {
            saveOptForObOracle(resourceId, resourceName, resourceType, optType, uid);
            return;
        }
        QueryWrapper<CoreOptRecent> updateWrapper = new QueryWrapper<>();
        if (resourceId != null) {
            updateWrapper.eq("resource_id", resourceId);
        }
        if (StringUtils.isNotEmpty(resourceName)) {
            updateWrapper.eq("resource_name", resourceName);
        }
        updateWrapper.eq("resource_type", resourceType);
        updateWrapper.eq("uid", uid);
        CoreOptRecent updateParam = new CoreOptRecent();
        updateParam.setOptType(optType);
        updateParam.setTime(System.currentTimeMillis());
        if (coreStoreMapper.update(updateParam, updateWrapper) == 0) {
            CoreOptRecent optRecent = new CoreOptRecent();
            optRecent.setId(IDUtils.snowID());
            optRecent.setResourceId(resourceId);
            optRecent.setResourceName(resourceName);
            optRecent.setResourceType(resourceType);
            optRecent.setOptType(optType);
            optRecent.setTime(System.currentTimeMillis());
            optRecent.setUid(AuthUtils.getUser().getUserId());
            coreStoreMapper.insert(optRecent);
        }
    }

    /**
     * 查询当前用户最近使用模板的时间映射
     */
    public Map<String, Long> findTemplateRecentUseTime() {
        Long uid = AuthUtils.getUser().getUserId();
        if (isObOracle()) {
            List<CoreOptRecent> result = jdbcTemplate.query("""
                    SELECT resource_name, "TIME" FROM core_workspace_recent_resource
                    WHERE resource_type = ? AND "UID" = ?
                    """, (rs, rowNum) -> {
                CoreOptRecent recent = new CoreOptRecent();
                recent.setResourceName(rs.getString("resource_name"));
                recent.setTime(rs.getLong("TIME"));
                return recent;
            }, OptConstants.OPT_RESOURCE_TYPE.TEMPLATE, uid);
            return recentUseTimeMap(result);
        }
        QueryWrapper<CoreOptRecent> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("resource_type", OptConstants.OPT_RESOURCE_TYPE.TEMPLATE);
        queryWrapper.eq("uid", uid);
        List<CoreOptRecent> result = coreStoreMapper.selectList(queryWrapper);
        return recentUseTimeMap(result);
    }

    // OB Oracle 无法直接使用 MyBatis Plus 处理 UID/TIME 保留字段，改用显式 SQL 写入
    private void saveOptForObOracle(Long resourceId, String resourceName, int resourceType, int optType, Long uid) {
        long now = System.currentTimeMillis();
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                UPDATE core_workspace_recent_resource
                SET opt_type = ?, "TIME" = ?
                WHERE resource_type = ? AND "UID" = ?
                """);
        args.add(optType);
        args.add(now);
        args.add(resourceType);
        args.add(uid);
        if (resourceId != null) {
            sql.append(" AND resource_id = ?");
            args.add(resourceId);
        }
        if (StringUtils.isNotEmpty(resourceName)) {
            sql.append(" AND resource_name = ?");
            args.add(resourceName);
        }
        if (jdbcTemplate.update(sql.toString(), args.toArray()) == 0) {
            jdbcTemplate.update("""
                    INSERT INTO core_workspace_recent_resource
                        (id, resource_id, resource_name, "UID", resource_type, opt_type, "TIME")
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, IDUtils.snowID(), resourceId, resourceName, uid, resourceType, optType, now);
        }
    }

    // 将最近访问记录压缩成资源名称到访问时间的映射
    private Map<String, Long> recentUseTimeMap(List<CoreOptRecent> result) {
        if (CollectionUtils.isNotEmpty(result)) {
            return result.stream().collect(Collectors.toMap(CoreOptRecent::getResourceName, CoreOptRecent::getTime));
        }
        return new HashMap<>();
    }

    // 最近访问表在 OB Oracle 下需要走保留字段兼容分支
    private boolean isObOracle() {
        return MetadataDbDialects.current(environment).type() == MetadataDbType.OB_ORACLE;
    }

}
