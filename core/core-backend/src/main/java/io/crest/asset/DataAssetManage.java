package io.crest.asset;

import io.crest.asset.dto.DataAssetDetailVO;
import io.crest.asset.dto.DataAssetImpactItemVO;
import io.crest.asset.dto.DataAssetImpactVO;
import io.crest.asset.dto.DataAssetOwnerVO;
import io.crest.asset.dto.DataAssetPageVO;
import io.crest.asset.dto.DataAssetProfileRequest;
import io.crest.asset.dto.DataAssetRequest;
import io.crest.asset.dto.DataAssetVO;
import io.crest.exception.CrestException;
import io.crest.metadata.MetadataDbDialect;
import io.crest.metadata.MetadataDbDialects;
import io.crest.result.ResultCode;
import io.crest.substitute.permissions.auth.PlatformPermissionManage;
import io.crest.utils.AuthUtils;
import io.crest.utils.IDUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据资产管理入口，负责聚合数据源、数据集、图表、可视化和分享资源，并统一套用权限与治理画像规则
 */
@Component
public class DataAssetManage {

    /**
     * 数据资产页直接使用 JDBC 拼接聚合查询，便于跨多张历史表保持一致字段
     */
    @Resource
    private JdbcTemplate jdbcTemplate;

    /**
     * 资产查询和管理判断依赖平台资源权限，所有列表与血缘查询都要复用同一套范围条件
     */
    @Resource
    private PlatformPermissionManage platformPermissionManage;

    @Resource
    private Environment environment;

    /**
     * 查询当前用户可见的数据资产分页，并补齐治理状态、负责人、权限和上下游数量
     */
    public DataAssetPageVO page(Integer page, Integer pageSize, DataAssetRequest request) {
        int normalizedPage = DataAssetUtils.normalizePage(page);
        int normalizedPageSize = DataAssetUtils.normalizePageSize(pageSize);
        DataAssetRequest normalized = normalizeRequest(request);
        QueryParts query = filteredAssets(normalized);

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(1) " + query.fromWhere, Long.class, query.args.toArray());
        List<Object> listArgs = new ArrayList<>(query.args);
        int offset = (normalizedPage - 1) * normalizedPageSize;
        String listSql = """
                SELECT a.asset_type,
                       a.asset_id,
                       a.name,
                       a.extra_type,
                       a.parent_asset_type,
                       a.parent_asset_id,
                       a.creator_id,
                       a.org_id,
                       a.create_time,
                       a.update_time,
                       p.description,
                       p.tags,
                       COALESCE(p.certified, 0) AS certified,
                       COALESCE(p.recommended, 0) AS recommended,
                       COALESCE(p.deprecated, 0) AS deprecated,
                       COALESCE(p.owner_id, a.creator_id) AS owner_id,
                       COALESCE(%s, %s, %s) AS owner_name,
                       COALESCE(%s, %s) AS creator_name,
                       COALESCE(%s, %s) AS org_name
                """.formatted(
                text("owner.name"),
                text("creator.name"),
                stringCast("COALESCE(p.owner_id, a.creator_id)"),
                text("creator.name"),
                stringCast("a.creator_id"),
                text("org.name"),
                stringLiteral("默认组织")
        ) + query.fromWhere + """

                ORDER BY COALESCE(p.deprecated, 0) ASC,
                         COALESCE(p.certified, 0) DESC,
                         COALESCE(p.recommended, 0) DESC,
                         a.update_time DESC,
                         a.asset_id DESC
                """;
        List<DataAssetVO> records = jdbcTemplate.query(dialect().limitOffset(listSql, normalizedPageSize, offset),
                (rs, rowNum) -> mapAsset(rs), listArgs.toArray());
        records.forEach(this::fillAssetAccessAndCounts);

        DataAssetPageVO vo = new DataAssetPageVO();
        vo.setPage(normalizedPage);
        vo.setPageSize(normalizedPageSize);
        vo.setTotal(total == null ? 0 : total);
        vo.setRecords(records);
        return vo;
    }

    /**
     * 读取单个资产详情，并同时返回它的直接上下游和影响面摘要
     */
    public DataAssetDetailVO detail(String assetType, String assetId) {
        DataAssetVO asset = loadAsset(assetType, assetId);
        DataAssetDetailVO detail = new DataAssetDetailVO();
        detail.setAsset(asset);
        detail.setUpstream(upstream(asset));
        detail.setDownstream(downstream(asset));
        detail.setImpact(impact(asset));
        return detail;
    }

    /**
     * 对外提供影响分析入口，先按权限加载资产，再计算该资产的下游影响
     */
    public DataAssetImpactVO impact(String assetType, String assetId) {
        return impact(loadAsset(assetType, assetId));
    }

    /**
     * 保存资产治理画像；只有具备管理权限的用户才能更新说明、标签、负责人和治理状态
     */
    @Transactional
    public DataAssetDetailVO saveProfile(DataAssetProfileRequest request) {
        if (request == null) {
            CrestException.throwException("资产信息不能为空");
        }
        String assetType = DataAssetUtils.requireAssetType(request.getAssetType());
        String assetId = StringUtils.trimToNull(request.getAssetId());
        if (assetId == null) {
            CrestException.throwException("资产ID不能为空");
        }
        DataAssetVO asset = loadAsset(assetType, assetId);
        if (!Boolean.TRUE.equals(asset.getCanManage())) {
            CrestException.throwException(ResultCode.PERMISSION_NO_ACCESS.code(), "当前用户无权维护该资产");
        }
        String description = trimToLength(request.getDescription(), 1024, "资产说明");
        String tags = trimToLength(request.getTags(), 1024, "标签");
        DataAssetUtils.GovernanceStatus status = DataAssetUtils.TYPE_DATASET.equals(assetType)
                ? DataAssetUtils.normalizeGovernanceStatus(
                request.getCertified(), request.getRecommended(), request.getDeprecated())
                : DataAssetUtils.emptyGovernanceStatus();
        Long uid = currentUid();
        long now = System.currentTimeMillis();
        int updated = jdbcTemplate.update("""
                UPDATE core_asset_profile
                SET description = ?,
                    owner_id = ?,
                    certified = ?,
                    recommended = ?,
                    deprecated = ?,
                    tags = ?,
                    update_time = ?,
                    update_by = ?
                WHERE asset_type = ? AND asset_id = ?
                """, description, request.getOwnerId(), bool(status.certified()), bool(status.recommended()),
                bool(status.deprecated()), tags, now, uid, assetType, assetId);
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO core_asset_profile
                        (id, asset_type, asset_id, description, owner_id, certified, recommended, deprecated, tags, create_time, update_time, update_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, IDUtils.snowID(), assetType, assetId, description, request.getOwnerId(),
                    bool(status.certified()), bool(status.recommended()), bool(status.deprecated()), tags, now, now, uid);
        }
        return detail(assetType, assetId);
    }

    /**
     * 返回可作为资产负责人的启用用户列表，供治理画像编辑表单选择
     */
    public List<DataAssetOwnerVO> owners() {
        return jdbcTemplate.query("""
                SELECT id, name, account
                FROM core_iam_user
                WHERE enable = 1
                ORDER BY name ASC, account ASC
                """, (rs, rowNum) -> {
            DataAssetOwnerVO vo = new DataAssetOwnerVO();
            vo.setId(rs.getLong("id"));
            vo.setName(rs.getString("name"));
            vo.setAccount(rs.getString("account"));
            return vo;
        });
    }

    /**
     * 规范化查询参数，保证空请求、空关键字和非法资产类型在进入 SQL 前被统一处理
     */
    private DataAssetRequest normalizeRequest(DataAssetRequest request) {
        DataAssetRequest normalized = request == null ? new DataAssetRequest() : request;
        normalized.setKeyword(StringUtils.trimToNull(normalized.getKeyword()));
        normalized.setAssetType(DataAssetUtils.normalizeAssetType(normalized.getAssetType()));
        return normalized;
    }

    /**
     * 按资产类型和编号加载单个资产，并在同一查询中应用当前用户权限
     */
    private DataAssetVO loadAsset(String assetType, String assetId) {
        DataAssetRequest request = new DataAssetRequest();
        request.setAssetType(DataAssetUtils.requireAssetType(assetType));
        QueryParts query = filteredAssets(request);
        query.fromWhere += " AND " + stringEquals("a.asset_id", "?");
        query.args.add(StringUtils.trimToEmpty(assetId));
        List<DataAssetVO> assets = jdbcTemplate.query(dialect().limitOne("""
                SELECT a.asset_type,
                       a.asset_id,
                       a.name,
                       a.extra_type,
                       a.parent_asset_type,
                       a.parent_asset_id,
                       a.creator_id,
                       a.org_id,
                       a.create_time,
                       a.update_time,
                       p.description,
                       p.tags,
                       COALESCE(p.certified, 0) AS certified,
                       COALESCE(p.recommended, 0) AS recommended,
                       COALESCE(p.deprecated, 0) AS deprecated,
                       COALESCE(p.owner_id, a.creator_id) AS owner_id,
                       COALESCE(%s, %s, %s) AS owner_name,
                       COALESCE(%s, %s) AS creator_name,
                       COALESCE(%s, %s) AS org_name
                """.formatted(
                text("owner.name"),
                text("creator.name"),
                stringCast("COALESCE(p.owner_id, a.creator_id)"),
                text("creator.name"),
                stringCast("a.creator_id"),
                text("org.name"),
                stringLiteral("默认组织")
        ) + query.fromWhere), (rs, rowNum) -> mapAsset(rs), query.args.toArray());
        if (assets.isEmpty()) {
            CrestException.throwException("资产不存在或无访问权限");
        }
        DataAssetVO asset = assets.get(0);
        fillAssetAccessAndCounts(asset);
        return asset;
    }

    /**
     * 构造资产列表的 FROM 和 WHERE 片段，集中处理关键字、治理状态、负责人和资产类型过滤
     */
    private QueryParts filteredAssets(DataAssetRequest request) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                FROM (
                """);
        sql.append(assetUnionSql());
        sql.append("""
                ) a
                LEFT JOIN core_asset_profile p
                    ON %s
                   AND %s
                LEFT JOIN core_iam_user owner ON owner.id = p.owner_id
                LEFT JOIN core_iam_user creator ON creator.id = a.creator_id
                LEFT JOIN core_iam_org org ON org.id = a.org_id
                WHERE 1 = 1
                """.formatted(stringEquals("p.asset_type", "a.asset_type"), stringEquals("p.asset_id", "a.asset_id")));
        if (request != null && StringUtils.isNotBlank(request.getAssetType())) {
            sql.append(" AND ").append(stringEquals("a.asset_type", "?"));
            args.add(request.getAssetType());
        }
        if (request != null && StringUtils.isNotBlank(request.getKeyword())) {
            sql.append("""
                    AND (
                        %s
                        OR %s
                        OR %s
                    )
                    """.formatted(
                    dialect().caseInsensitiveLike("a.name", "?"),
                    dialect().caseInsensitiveLike("a.asset_id", "?"),
                    dialect().caseInsensitiveLike("COALESCE(p.tags, '')", "?")
            ));
            args.add(request.getKeyword());
            args.add(request.getKeyword());
            args.add(request.getKeyword());
        }
        if (request != null && request.getCertified() != null) {
            sql.append(" AND COALESCE(p.certified, 0) = ?");
            args.add(bool(request.getCertified()));
            if (Boolean.TRUE.equals(request.getCertified())) {
                sql.append(" AND COALESCE(p.deprecated, 0) = 0");
            }
        }
        if (request != null && request.getRecommended() != null) {
            sql.append(" AND COALESCE(p.recommended, 0) = ?");
            args.add(bool(request.getRecommended()));
            if (Boolean.TRUE.equals(request.getRecommended())) {
                sql.append(" AND COALESCE(p.deprecated, 0) = 0");
            }
        }
        if (request != null && request.getDeprecated() != null) {
            sql.append(" AND COALESCE(p.deprecated, 0) = ?");
            args.add(bool(request.getDeprecated()));
        }
        if (request != null && request.getOwnerId() != null) {
            sql.append(" AND COALESCE(p.owner_id, a.creator_id) = ?");
            args.add(request.getOwnerId());
        }
        return new QueryParts(sql.toString(), args);
    }

    /**
     * 将不同来源的资产统一投影为相同列结构，供列表、详情和过滤逻辑复用
     */
    private String assetUnionSql() {
        List<String> selects = new ArrayList<>();
        selects.add("""
                SELECT %s AS asset_type,
                       %s AS asset_id,
                       %s AS name,
                       %s AS extra_type,
                       %s AS parent_asset_type,
                       %s AS parent_asset_id,
                       COALESCE(ds_ri.creator, %s) AS creator_id,
                       COALESCE(ds_ri.oid, 1) AS org_id,
                       ds.create_time AS create_time,
                       ds.update_time AS update_time
                FROM core_datasource ds
                LEFT JOIN core_iam_resource_index ds_ri
                    ON %s
                   AND %s
                WHERE %s
                """.formatted(
                stringLiteral("datasource"),
                stringCast("ds.id"),
                text("ds.name"),
                text("ds.type"),
                nullableString(),
                nullableString(),
                numberCast("ds.create_by"),
                stringEquals("ds_ri.resource_type", stringLiteral("datasource")),
                stringEquals("ds_ri.resource_id", stringCast("ds.id")),
                stringNotEquals("ds.type", stringLiteral("folder"))
        ) + permissionClause(platformPermissionManage.resourceScopeSql("datasource", "ds.id", "ds.create_by", null)));
        selects.add("""
                SELECT %s AS asset_type,
                       %s AS asset_id,
                       COALESCE(NULLIF(%s, %s), %s, %s) AS name,
                       %s AS extra_type,
                       %s AS parent_asset_type,
                       %s AS parent_asset_id,
                       COALESCE(dg_table_ri.creator, %s) AS creator_id,
                       COALESCE(dg_table_ri.oid, 1) AS org_id,
                       dg.create_time AS create_time,
                       dg.last_update_time AS update_time
                FROM core_dataset_table dt
                INNER JOIN core_dataset dg ON dg.id = dt.dataset_group_id AND %s
                LEFT JOIN core_iam_resource_index dg_table_ri
                    ON %s
                   AND %s
                WHERE 1 = 1
                """.formatted(
                stringLiteral("table"),
                stringCast("dt.id"),
                text("dt.name"),
                stringLiteral(""),
                text("dt.table_name"),
                stringCast("dt.id"),
                text("dt.type"),
                stringLiteral("dataset"),
                stringCast("dg.id"),
                numberCast("dg.create_by"),
                stringEquals("dg.node_type", stringLiteral("dataset")),
                stringEquals("dg_table_ri.resource_type", stringLiteral("dataset")),
                stringEquals("dg_table_ri.resource_id", stringCast("dg.id"))
        ) + permissionClause(platformPermissionManage.resourceScopeSql("dataset", "dg.id", "dg.create_by", null)));
        selects.add("""
                SELECT %s AS asset_type,
                       %s AS asset_id,
                       %s AS name,
                       %s AS extra_type,
                       %s AS parent_asset_type,
                       %s AS parent_asset_id,
                       COALESCE(dg_ri.creator, %s) AS creator_id,
                       COALESCE(dg_ri.oid, 1) AS org_id,
                       dg.create_time AS create_time,
                       dg.last_update_time AS update_time
                FROM core_dataset dg
                LEFT JOIN core_iam_resource_index dg_ri
                    ON %s
                   AND %s
                WHERE %s
                """.formatted(
                stringLiteral("dataset"),
                stringCast("dg.id"),
                text("dg.name"),
                text("dg.type"),
                nullableString(),
                nullableString(),
                numberCast("dg.create_by"),
                stringEquals("dg_ri.resource_type", stringLiteral("dataset")),
                stringEquals("dg_ri.resource_id", stringCast("dg.id")),
                stringEquals("dg.node_type", stringLiteral("dataset"))
        ) + permissionClause(platformPermissionManage.resourceScopeSql("dataset", "dg.id", "dg.create_by", null)));
        selects.add("""
                SELECT %s AS asset_type,
                       %s AS asset_id,
                       COALESCE(NULLIF(%s, %s), %s) AS name,
                       %s AS extra_type,
                       CASE WHEN %s THEN %s ELSE %s END AS parent_asset_type,
                       %s AS parent_asset_id,
                       %s AS creator_id,
                       COALESCE(%s, 1) AS org_id,
                       c.create_time AS create_time,
                       c.update_time AS update_time
                FROM core_chart_view c
                INNER JOIN core_visualization v ON v.id = c.scene_id
                WHERE v.delete_flag = 0
                  AND %s
                """.formatted(
                stringLiteral("chart"),
                stringCast("c.id"),
                text("c.title"),
                stringLiteral(""),
                stringCast("c.id"),
                text("c.type"),
                stringEquals("v.type", stringLiteral("dataV")),
                stringLiteral("screen"),
                stringLiteral("panel"),
                stringCast("v.id"),
                numberCast("c.create_by"),
                numberCast("NULLIF(v.org_id, '')"),
                stringEquals("v.node_type", stringLiteral("leaf"))
        ) + permissionClause(visualizationScopeSql("v")));
        selects.add(visualizationSelect("panel", "dashboard"));
        selects.add(visualizationSelect("screen", "dataV"));
        selects.add("""
                SELECT %s AS asset_type,
                       %s AS asset_id,
                       %s AS name,
                       %s AS extra_type,
                       CASE WHEN %s THEN %s ELSE %s END AS parent_asset_type,
                       %s AS parent_asset_id,
                       s.creator AS creator_id,
                       s.oid AS org_id,
                       s.time AS create_time,
                       s.time AS update_time
                FROM core_share_link s
                INNER JOIN core_visualization v ON v.id = s.resource_id
                WHERE v.delete_flag = 0
                  AND %s
                """.formatted(
                stringLiteral("share"),
                stringCast("s.id"),
                concat("COALESCE(" + text("v.name") + ", " + stringLiteral("可视化资源") + ")",
                        stringLiteral(" / "), text("s.uuid")),
                text("s.uuid"),
                stringEquals("v.type", stringLiteral("dataV")),
                stringLiteral("screen"),
                stringLiteral("panel"),
                stringCast("v.id"),
                stringEquals("v.node_type", stringLiteral("leaf"))
        ) + permissionClause(visualizationScopeSql("v")));
        return String.join("\nUNION ALL\n", selects);
    }

    /**
     * 生成仪表板或大屏资源的资产查询片段，并按资源类型套用对应权限条件
     */
    private String visualizationSelect(String assetType, String visualizationType) {
        String resourceType = DataAssetUtils.TYPE_SCREEN.equals(assetType) ? "screen" : "panel";
        return """
                SELECT %s AS asset_type,
                       %s AS asset_id,
                       %s AS name,
                       %s AS extra_type,
                       %s AS parent_asset_type,
                       %s AS parent_asset_id,
                       %s AS creator_id,
                       COALESCE(%s, 1) AS org_id,
                       v.create_time AS create_time,
                       v.update_time AS update_time
                FROM core_visualization v
                WHERE v.delete_flag = 0
                  AND %s
                  AND %s
                """.formatted(
                stringLiteral(assetType),
                stringCast("v.id"),
                text("v.name"),
                text("v.type"),
                nullableString(),
                nullableString(),
                numberCast("v.create_by"),
                numberCast("NULLIF(v.org_id, '')"),
                stringEquals("v.node_type", stringLiteral("leaf")),
                stringEquals("v.type", stringLiteral(visualizationType))
        ) +
                permissionClause(platformPermissionManage.resourceScopeSql(resourceType, "v.id", "v.create_by", "v.org_id"));
    }

    /**
     * 补齐资产展示标签、管理权限和上下游数量，保持列表与详情中的资产摘要一致
     */
    private void fillAssetAccessAndCounts(DataAssetVO asset) {
        asset.setAssetTypeLabel(DataAssetUtils.assetTypeLabel(asset.getAssetType()));
        asset.setCanManage(canManage(asset));
        asset.setUpstreamCount(upstreamCount(asset));
        asset.setDownstreamCount(downstreamCount(asset));
    }

    /**
     * 判断当前用户是否可维护资产画像，优先识别系统管理员、创建人和组织管理员
     */
    private boolean canManage(DataAssetVO asset) {
        Long uid = currentUid();
        if (uid == null || platformPermissionManage.isSystemAdmin(uid)) {
            return true;
        }
        if (asset.getCreatorId() != null && asset.getCreatorId().equals(uid)) {
            return true;
        }
        if (asset.getOrgId() != null && platformPermissionManage.isOrgAdmin(uid, asset.getOrgId())) {
            return true;
        }
        String resourceType = DataAssetUtils.resourceTypeForAsset(asset.getAssetType());
        String resourceId = asset.getAssetId();
        if (resourceType == null && StringUtils.isNotBlank(asset.getParentAssetType())) {
            resourceType = DataAssetUtils.resourceTypeForAsset(asset.getParentAssetType());
            resourceId = asset.getParentAssetId();
        }
        return resourceType != null
                && StringUtils.isNotBlank(resourceId)
                && platformPermissionManage.resourceWeight(resourceType, resourceId,
                asset.getCreatorId() == null ? null : String.valueOf(asset.getCreatorId()), asset.getOrgId()) >= 7;
    }

    /**
     * 查询资产的直接上游，方向表示当前资产依赖哪些数据或可视化资源
     */
    private List<DataAssetImpactItemVO> upstream(DataAssetVO asset) {
        Long id = longId(asset.getAssetId());
        if (id == null) {
            return List.of();
        }
        return switch (asset.getAssetType()) {
            case DataAssetUtils.TYPE_TABLE -> queryItems("""
                    SELECT %s AS asset_type, %s AS asset_id, dg.name AS name,
                           '所属数据集' AS relation, dg.last_update_time AS update_time
                    FROM core_dataset_table dt
                    INNER JOIN core_dataset dg ON dg.id = dt.dataset_group_id
                    WHERE dt.id = ?
                    """.formatted(stringLiteral("dataset"), stringCast("dg.id"))
                    + permissionClause(platformPermissionManage.resourceScopeSql("dataset", "dg.id", "dg.create_by", null)), id);
            case DataAssetUtils.TYPE_DATASET -> datasetUpstream(id);
            case DataAssetUtils.TYPE_CHART -> queryItems("""
                    SELECT %s AS asset_type, %s AS asset_id, dg.name AS name,
                           '图表使用的数据集' AS relation, dg.last_update_time AS update_time
                    FROM core_chart_view c
                    INNER JOIN core_dataset dg ON dg.id = c.table_id
                    WHERE c.id = ?
                    """.formatted(stringLiteral("dataset"), stringCast("dg.id"))
                    + permissionClause(platformPermissionManage.resourceScopeSql("dataset", "dg.id", "dg.create_by", null)), id);
            case DataAssetUtils.TYPE_PANEL, DataAssetUtils.TYPE_SCREEN -> panelUpstream(id);
            case DataAssetUtils.TYPE_SHARE -> shareUpstream(id);
            default -> List.of();
        };
    }

    /**
     * 查询资产的直接下游，方向表示当前资产被哪些资源继续使用或分享
     */
    private List<DataAssetImpactItemVO> downstream(DataAssetVO asset) {
        Long id = longId(asset.getAssetId());
        if (id == null) {
            return List.of();
        }
        return switch (asset.getAssetType()) {
            case DataAssetUtils.TYPE_DATASOURCE -> datasourceDownstream(id);
            case DataAssetUtils.TYPE_DATASET -> datasetDownstream(id);
            case DataAssetUtils.TYPE_PANEL, DataAssetUtils.TYPE_SCREEN -> visualizationDownstream(id);
            default -> List.of();
        };
    }

    /**
     * 汇总资产下游影响列表，并按资产类型生成影响面数量摘要
     */
    private DataAssetImpactVO impact(DataAssetVO asset) {
        DataAssetImpactVO vo = new DataAssetImpactVO();
        vo.setAssetType(asset.getAssetType());
        vo.setAssetId(asset.getAssetId());
        List<DataAssetImpactItemVO> items = downstream(asset);
        vo.setItems(items);
        Map<String, Long> summary = new LinkedHashMap<>();
        for (DataAssetImpactItemVO item : items) {
            summary.merge(item.getAssetType(), 1L, Long::sum);
        }
        vo.setSummary(summary);
        return vo;
    }

    /**
     * 数据集上游包含原始数据源和组成该数据集的物理表
     */
    private List<DataAssetImpactItemVO> datasetUpstream(Long datasetId) {
        List<DataAssetImpactItemVO> items = new ArrayList<>();
        items.addAll(queryItems("""
                SELECT DISTINCT %s AS asset_type, %s AS asset_id, ds.name AS name,
                       '提供数据表' AS relation, ds.update_time AS update_time
                FROM core_datasource ds
                INNER JOIN core_dataset_table dt ON dt.datasource_id = ds.id
                WHERE dt.dataset_group_id = ?
                  AND %s
                """.formatted(
                stringLiteral("datasource"),
                stringCast("ds.id"),
                stringNotEquals("ds.type", stringLiteral("folder"))
        ) + permissionClause(platformPermissionManage.resourceScopeSql("datasource", "ds.id", "ds.create_by", null)), datasetId));
        items.addAll(queryItems("""
                SELECT %s AS asset_type, %s AS asset_id,
                       COALESCE(NULLIF(dt.name, ''), dt.table_name, %s) AS name,
                       '组成数据集' AS relation, NULL AS update_time
                FROM core_dataset_table dt
                WHERE dt.dataset_group_id = ?
                ORDER BY dt.id ASC
                """.formatted(stringLiteral("table"), stringCast("dt.id"), stringCast("dt.id")), datasetId));
        return items;
    }

    /**
     * 数据集下游覆盖图表、包含这些图表的可视化资源、分享链接和缓存任务
     */
    private List<DataAssetImpactItemVO> datasetDownstream(Long datasetId) {
        List<DataAssetImpactItemVO> items = new ArrayList<>();
        String visualizationScope = permissionClause(visualizationScopeSql("v"));
        items.addAll(queryItems("""
                SELECT %s AS asset_type, %s AS asset_id,
                       COALESCE(NULLIF(c.title, ''), %s) AS name,
                       '使用该数据集' AS relation, c.update_time AS update_time
                FROM core_chart_view c
                INNER JOIN core_visualization v ON v.id = c.scene_id
                WHERE c.table_id = ?
                  AND v.delete_flag = 0
                  AND %s
                """.formatted(
                stringLiteral("chart"),
                stringCast("c.id"),
                stringCast("c.id"),
                stringEquals("v.node_type", stringLiteral("leaf"))
        ) + visualizationScope + """
                ORDER BY c.update_time DESC
                """, datasetId));
        items.addAll(queryItems("""
                SELECT CASE WHEN %s THEN %s ELSE %s END AS asset_type,
                       %s AS asset_id,
                       v.name AS name,
                       '包含使用该数据集的图表' AS relation,
                       v.update_time AS update_time
                FROM core_visualization v
                INNER JOIN core_chart_view c ON c.scene_id = v.id
                WHERE c.table_id = ?
                  AND v.delete_flag = 0
                  AND %s
                """.formatted(
                stringEquals("v.type", stringLiteral("dataV")),
                stringLiteral("screen"),
                stringLiteral("panel"),
                stringCast("v.id"),
                stringEquals("v.node_type", stringLiteral("leaf"))
        ) + visualizationScope + """
                GROUP BY v.id, v.type, v.name, v.update_time
                ORDER BY v.update_time DESC
                """, datasetId));
        items.addAll(queryItems("""
                SELECT %s AS asset_type,
                       %s AS asset_id,
                       %s AS name,
                       '分享了受影响资源' AS relation,
                       s.time AS update_time
                FROM core_share_link s
                INNER JOIN core_visualization v ON v.id = s.resource_id
                INNER JOIN core_chart_view c ON c.scene_id = v.id
                WHERE c.table_id = ?
                  AND v.delete_flag = 0
                  AND %s
                """.formatted(
                stringLiteral("share"),
                stringCast("s.id"),
                concat("v.name", stringLiteral(" / "), "s.uuid"),
                stringEquals("v.node_type", stringLiteral("leaf"))
        ) + visualizationScope + """
                GROUP BY s.id, v.name, s.uuid, s.time
                ORDER BY s.time DESC
                """, datasetId));
        items.addAll(queryItems("""
                SELECT %s AS asset_type,
                       %s AS asset_id,
                       COALESCE(NULLIF(t.name, ''), '数据集缓存任务') AS name,
                       '该数据集的缓存任务' AS relation,
                       t.update_time AS update_time
                FROM core_dataset_sync_task t
                WHERE t.dataset_group_id = ?
                """.formatted(stringLiteral("cacheTask"), stringCast("t.id")), datasetId));
        return items;
    }

    /**
     * 数据源下游只统计引用该数据源表的数据集，并继续沿用数据集资源权限
     */
    private List<DataAssetImpactItemVO> datasourceDownstream(Long datasourceId) {
        return queryItems("""
                SELECT DISTINCT %s AS asset_type,
                       %s AS asset_id,
                       dg.name AS name,
                       '引用该数据源的数据集' AS relation,
                       dg.last_update_time AS update_time
                FROM core_dataset dg
                INNER JOIN core_dataset_table dt ON dt.dataset_group_id = dg.id
                WHERE %s
                  AND dt.datasource_id = ?
                """.formatted(
                stringLiteral("dataset"),
                stringCast("dg.id"),
                stringEquals("dg.node_type", stringLiteral("dataset"))
        ) + permissionClause(platformPermissionManage.resourceScopeSql("dataset", "dg.id", "dg.create_by", null)), datasourceId);
    }

    /**
     * 可视化资源上游包含自身图表，以及这些图表使用的数据集
     */
    private List<DataAssetImpactItemVO> panelUpstream(Long visualizationId) {
        List<DataAssetImpactItemVO> items = new ArrayList<>();
        items.addAll(queryItems("""
                SELECT %s AS asset_type, %s AS asset_id,
                       COALESCE(NULLIF(c.title, ''), %s) AS name,
                       '属于当前资源' AS relation,
                       c.update_time AS update_time
                FROM core_chart_view c
                WHERE c.scene_id = ?
                ORDER BY c.update_time DESC
                """.formatted(stringLiteral("chart"), stringCast("c.id"), stringCast("c.id")), visualizationId));
        items.addAll(queryItems("""
                SELECT DISTINCT %s AS asset_type, %s AS asset_id,
                       dg.name AS name,
                       '当前资源使用的数据集' AS relation,
                       dg.last_update_time AS update_time
                FROM core_chart_view c
                INNER JOIN core_dataset dg ON dg.id = c.table_id
                WHERE c.scene_id = ?
                """.formatted(stringLiteral("dataset"), stringCast("dg.id"))
                + permissionClause(platformPermissionManage.resourceScopeSql("dataset", "dg.id", "dg.create_by", null)), visualizationId));
        return items;
    }

    /**
     * 仪表板或大屏下游当前只包含基于该资源生成的分享链接
     */
    private List<DataAssetImpactItemVO> visualizationDownstream(Long visualizationId) {
        return queryItems("""
                SELECT %s AS asset_type,
                       %s AS asset_id,
                       s.uuid AS name,
                       '分享当前资源' AS relation,
                       s.time AS update_time
                FROM core_share_link s
                WHERE s.resource_id = ?
                ORDER BY s.time DESC
                """.formatted(stringLiteral("share"), stringCast("s.id")), visualizationId);
    }

    /**
     * 分享链接上游回溯到被分享的仪表板或大屏，并按可视化权限过滤
     */
    private List<DataAssetImpactItemVO> shareUpstream(Long shareId) {
        return queryItems("""
                SELECT CASE WHEN %s THEN %s ELSE %s END AS asset_type,
                       %s AS asset_id,
                       v.name AS name,
                       '被分享资源' AS relation,
                       v.update_time AS update_time
                FROM core_share_link s
                INNER JOIN core_visualization v ON v.id = s.resource_id
                WHERE s.id = ?
                """.formatted(
                stringEquals("v.type", stringLiteral("dataV")),
                stringLiteral("screen"),
                stringLiteral("panel"),
                stringCast("v.id")
        ) + permissionClause(visualizationScopeSql("v")), shareId);
    }

    /**
     * 计算直接上游数量，和 upstream 方法保持同一资产类型分支
     */
    private Integer upstreamCount(DataAssetVO asset) {
        Long id = longId(asset.getAssetId());
        if (id == null) {
            return 0;
        }
        return switch (asset.getAssetType()) {
            case DataAssetUtils.TYPE_TABLE -> count("""
                    SELECT COUNT(1)
                    FROM core_dataset_table dt
                    INNER JOIN core_dataset dg ON dg.id = dt.dataset_group_id
                    WHERE dt.id = ?
                    """ + permissionClause(platformPermissionManage.resourceScopeSql("dataset", "dg.id", "dg.create_by", null)), id);
            case DataAssetUtils.TYPE_DATASET -> count("""
                    SELECT COUNT(DISTINCT ds.id)
                FROM core_datasource ds
                INNER JOIN core_dataset_table dt ON dt.datasource_id = ds.id
                WHERE dt.dataset_group_id = ?
                      AND %s
                    """.formatted(stringNotEquals("ds.type", stringLiteral("folder")))
                    + permissionClause(platformPermissionManage.resourceScopeSql("datasource", "ds.id", "ds.create_by", null)), id)
                    + count("SELECT COUNT(1) FROM core_dataset_table dt WHERE dt.dataset_group_id = ?", id);
            case DataAssetUtils.TYPE_CHART -> count("""
                    SELECT COUNT(1)
                    FROM core_chart_view c
                    INNER JOIN core_dataset dg ON dg.id = c.table_id
                    WHERE c.id = ?
                    """ + permissionClause(platformPermissionManage.resourceScopeSql("dataset", "dg.id", "dg.create_by", null)), id);
            case DataAssetUtils.TYPE_PANEL, DataAssetUtils.TYPE_SCREEN -> count("SELECT COUNT(1) FROM core_chart_view c WHERE c.scene_id = ?", id)
                    + count("""
                    SELECT COUNT(DISTINCT dg.id)
                    FROM core_chart_view c
                    INNER JOIN core_dataset dg ON dg.id = c.table_id
                    WHERE c.scene_id = ?
                    """ + permissionClause(platformPermissionManage.resourceScopeSql("dataset", "dg.id", "dg.create_by", null)), id);
            case DataAssetUtils.TYPE_SHARE -> count("""
                    SELECT COUNT(1)
                    FROM core_share_link s
                    INNER JOIN core_visualization v ON v.id = s.resource_id
                    WHERE s.id = ?
                    """ + permissionClause(visualizationScopeSql("v")), id);
            default -> 0;
        };
    }

    /**
     * 计算直接下游数量，和 downstream 方法保持同一资产类型分支
     */
    private Integer downstreamCount(DataAssetVO asset) {
        Long id = longId(asset.getAssetId());
        if (id == null) {
            return 0;
        }
        return switch (asset.getAssetType()) {
            case DataAssetUtils.TYPE_DATASOURCE -> count("""
                    SELECT COUNT(DISTINCT dg.id)
                    FROM core_dataset dg
                    INNER JOIN core_dataset_table dt ON dt.dataset_group_id = dg.id
                    WHERE %s
                      AND dt.datasource_id = ?
                    """.formatted(stringEquals("dg.node_type", stringLiteral("dataset")))
                    + permissionClause(platformPermissionManage.resourceScopeSql("dataset", "dg.id", "dg.create_by", null)), id);
            case DataAssetUtils.TYPE_DATASET -> datasetDownstreamCount(id);
            case DataAssetUtils.TYPE_PANEL, DataAssetUtils.TYPE_SCREEN -> count("SELECT COUNT(1) FROM core_share_link s WHERE s.resource_id = ?", id);
            default -> 0;
        };
    }

    /**
     * 数据集下游数量需要合并图表、可视化资源、分享链接和缓存任务四类影响
     */
    private Integer datasetDownstreamCount(Long datasetId) {
        String visualizationScope = permissionClause(visualizationScopeSql("v"));
        return count("""
                SELECT COUNT(DISTINCT c.id)
                FROM core_chart_view c
                INNER JOIN core_visualization v ON v.id = c.scene_id
                WHERE c.table_id = ?
                  AND v.delete_flag = 0
                  AND %s
                """.formatted(stringEquals("v.node_type", stringLiteral("leaf"))) + visualizationScope, datasetId)
                + count("""
                SELECT COUNT(DISTINCT v.id)
                FROM core_visualization v
                INNER JOIN core_chart_view c ON c.scene_id = v.id
                WHERE c.table_id = ?
                  AND v.delete_flag = 0
                  AND %s
                """.formatted(stringEquals("v.node_type", stringLiteral("leaf"))) + visualizationScope, datasetId)
                + count("""
                SELECT COUNT(DISTINCT s.id)
                FROM core_share_link s
                INNER JOIN core_visualization v ON v.id = s.resource_id
                INNER JOIN core_chart_view c ON c.scene_id = v.id
                WHERE c.table_id = ?
                  AND v.delete_flag = 0
                  AND %s
                """.formatted(stringEquals("v.node_type", stringLiteral("leaf"))) + visualizationScope, datasetId)
                + count("SELECT COUNT(1) FROM core_dataset_sync_task t WHERE t.dataset_group_id = ?", datasetId);
    }

    /**
     * 执行计数查询并将空结果归零，统一处理 COUNT 查询的返回类型
     */
    private Integer count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value.intValue();
    }

    /**
     * 执行影响项查询，并把资产类型、关系说明和更新时间映射为统一 VO
     */
    private List<DataAssetImpactItemVO> queryItems(String sql, Object... args) {
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            DataAssetImpactItemVO vo = new DataAssetImpactItemVO();
            vo.setAssetType(rs.getString("asset_type"));
            vo.setAssetTypeLabel(DataAssetUtils.assetTypeLabel(vo.getAssetType()));
            vo.setAssetId(rs.getString("asset_id"));
            vo.setName(rs.getString("name"));
            vo.setRelation(rs.getString("relation"));
            vo.setUpdateTime(nullableLong(rs, "update_time"));
            return vo;
        }, args);
    }

    /**
     * 将聚合资产查询结果映射为资产摘要对象，并规范化治理状态
     */
    private DataAssetVO mapAsset(ResultSet rs) throws SQLException {
        DataAssetVO vo = new DataAssetVO();
        vo.setAssetType(rs.getString("asset_type"));
        vo.setAssetId(rs.getString("asset_id"));
        vo.setName(rs.getString("name"));
        vo.setExtraType(rs.getString("extra_type"));
        vo.setParentAssetType(rs.getString("parent_asset_type"));
        vo.setParentAssetId(rs.getString("parent_asset_id"));
        vo.setCreatorId(nullableLong(rs, "creator_id"));
        vo.setOrgId(nullableLong(rs, "org_id"));
        vo.setCreateTime(nullableLong(rs, "create_time"));
        vo.setUpdateTime(nullableLong(rs, "update_time"));
        vo.setDescription(rs.getString("description"));
        vo.setTags(rs.getString("tags"));
        DataAssetUtils.GovernanceStatus governanceStatus = DataAssetUtils.normalizeGovernanceStatus(
                rs.getBoolean("certified"),
                rs.getBoolean("recommended"),
                rs.getBoolean("deprecated")
        );
        vo.setCertified(governanceStatus.certified());
        vo.setRecommended(governanceStatus.recommended());
        vo.setDeprecated(governanceStatus.deprecated());
        vo.setOwnerId(nullableLong(rs, "owner_id"));
        vo.setOwnerName(rs.getString("owner_name"));
        vo.setCreatorName(rs.getString("creator_name"));
        vo.setOrgName(rs.getString("org_name"));
        vo.setAssetTypeLabel(DataAssetUtils.assetTypeLabel(vo.getAssetType()));
        return vo;
    }

    /**
     * 根据可视化类型组合仪表板和大屏权限 SQL，避免大屏用户看到无权限仪表板资源
     */
    private String visualizationScopeSql(String alias) {
        String panelScope = platformPermissionManage.resourceScopeSql("panel", alias + ".id", alias + ".create_by", alias + ".org_id");
        String screenScope = platformPermissionManage.resourceScopeSql("screen", alias + ".id", alias + ".create_by", alias + ".org_id");
        if (StringUtils.isBlank(panelScope) && StringUtils.isBlank(screenScope)) {
            return null;
        }
        if (StringUtils.isBlank(panelScope) || StringUtils.isBlank(screenScope)) {
            return StringUtils.defaultIfBlank(panelScope, screenScope);
        }
        return "((" + stringEquals(alias + ".type", stringLiteral("dashboard")) + " AND "
                + panelScope + ") OR (" + stringEquals(alias + ".type", stringLiteral("dataV")) + " AND "
                + screenScope + "))";
    }

    /**
     * 将可选权限范围转换成可直接拼接到 WHERE 后的 AND 片段
     */
    private String permissionClause(String scopeSql) {
        return StringUtils.isBlank(scopeSql) ? "" : "\n AND " + scopeSql;
    }

    // 读取当前元数据库方言，以下 SQL 片段都通过它屏蔽 MySQL 与 OB Oracle 差异
    private MetadataDbDialect dialect() {
        return MetadataDbDialects.current(environment);
    }

    // 生成带数据库转义规则的字符串常量
    private String stringLiteral(String value) {
        return dialect().stringLiteral(value);
    }

    // 将字段表达式转为可比较的字符串表达式
    private String stringCast(String expression) {
        return dialect().stringCast(expression);
    }

    // 生成与当前方言兼容的空字符串占位表达式
    private String nullableString() {
        return dialect().nullableString();
    }

    // 对普通文本表达式补充当前方言需要的字符集或排序规则
    private String text(String expression) {
        return dialect().collate(expression);
    }

    // 生成跨方言一致的字符串相等条件
    private String stringEquals(String leftExpression, String rightExpression) {
        return dialect().stringEquals(leftExpression, rightExpression);
    }

    // 生成跨方言一致的字符串不等条件
    private String stringNotEquals(String leftExpression, String rightExpression) {
        return dialect().stringNotEquals(leftExpression, rightExpression);
    }

    // 将资源编号等文本表达式转换为数值表达式
    private String numberCast(String expression) {
        return dialect().numberCast(expression);
    }

    // 生成跨方言一致的字符串拼接表达式
    private String concat(String... expressions) {
        return dialect().concat(expressions);
    }

    /**
     * 读取当前登录用户编号，未登录上下文返回空值供权限逻辑兜底
     */
    private Long currentUid() {
        return AuthUtils.getUser() == null ? null : AuthUtils.getUser().getUserId();
    }

    /**
     * 将资产编号转换为长整型，非数字编号在血缘查询中按无结果处理
     */
    private Long longId(String value) {
        try {
            return StringUtils.isBlank(value) ? null : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 读取可空长整型字段，保留数据库 NULL 和数字 0 的语义差异
     */
    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    /**
     * 将布尔治理状态转换成数据库中的 0/1 表示
     */
    private int bool(Boolean value) {
        return Boolean.TRUE.equals(value) ? 1 : 0;
    }

    /**
     * 统一裁剪画像文本并校验最大长度，避免不同字段各自实现限制
     */
    private String trimToLength(String value, int max, String label) {
        String trimmed = StringUtils.trimToNull(value);
        if (trimmed != null && trimmed.length() > max) {
            CrestException.throwException(label + "不能超过 " + max + " 个字符");
        }
        return trimmed;
    }

    /**
     * 保存动态 SQL 片段和对应参数，避免拼接 WHERE 时丢失参数顺序
     */
    private static class QueryParts {
        private String fromWhere;
        private final List<Object> args;

        private QueryParts(String fromWhere, List<Object> args) {
            this.fromWhere = fromWhere;
            this.args = args;
        }
    }
}
