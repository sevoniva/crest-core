package io.crest.portal;

import io.crest.commons.constants.OptConstants;
import io.crest.exception.CrestException;
import io.crest.metadata.MetadataDbDialect;
import io.crest.metadata.MetadataDbDialects;
import io.crest.operation.manage.CoreOptRecentManage;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/data-portal")
/**
 * 数据门户接口控制器，提供资源概览、列表和详情查询
 */
public class DataPortalServer {

    private static final int MAX_PAGE_SIZE = 100;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private DataPortalPermissionManage dataPortalPermissionManage;

    @Resource
    private CoreOptRecentManage coreOptRecentManage;

    @Resource
    private Environment environment;

    /**
     * 查询当前用户可访问资源的门户概览
     */
    @GetMapping("/overview")
    public DataPortalOverviewVO overview() {
        Long uid = dataPortalPermissionManage.currentUid();
        QueryParts query = buildBaseQuery(uid, null);
        List<Object> args = new ArrayList<>(query.args);
        DataPortalOverviewVO overview = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) AS total,
                       SUM(CASE WHEN v.type = 'dataV' THEN 1 ELSE 0 END) AS screen_count,
                       SUM(CASE WHEN v.type = 'dashboard' THEN 1 ELSE 0 END) AS dashboard_count,
                       MAX(v.update_time) AS latest_update_time
                """ + query.fromWhere, (rs, rowNum) -> {
            DataPortalOverviewVO vo = new DataPortalOverviewVO();
            vo.setTotal(rs.getLong("total"));
            vo.setScreenCount(rs.getLong("screen_count"));
            vo.setDashboardCount(rs.getLong("dashboard_count"));
            long latest = rs.getLong("latest_update_time");
            vo.setLatestUpdateTime(rs.wasNull() ? null : latest);
            vo.setBackendAccess(dataPortalPermissionManage.hasBackendAccess(uid));
            return vo;
        }, args.toArray());
        return overview == null ? new DataPortalOverviewVO() : overview;
    }

    /**
     * 分页查询当前用户可访问的数据门户资源
     */
    @PostMapping("/resources")
    public DataPortalPageVO resources(@RequestBody(required = false) DataPortalResourceRequest request) {
        Long uid = dataPortalPermissionManage.currentUid();
        DataPortalResourceRequest normalized = normalizeRequest(request);
        QueryParts query = buildBaseQuery(uid, normalized);
        QueryParts countQuery = buildBaseQuery(uid, copyWithoutType(normalized));

        // query.fromWhere is assembled only from internal SQL fragments and bind placeholders after request normalization.
        long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) " + query.fromWhere, // nosemgrep: java.spring.security.injection.tainted-sql-string.tainted-sql-string
                Long.class,
                query.args.toArray()
        );
        DataPortalPageVO page = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) AS total,
                       SUM(CASE WHEN v.type = 'dataV' THEN 1 ELSE 0 END) AS screen_count,
                       SUM(CASE WHEN v.type = 'dashboard' THEN 1 ELSE 0 END) AS dashboard_count
                """ + countQuery.fromWhere, (rs, rowNum) -> {
            DataPortalPageVO vo = new DataPortalPageVO();
            vo.setTotal(total);
            vo.setScreenCount(rs.getLong("screen_count"));
            vo.setDashboardCount(rs.getLong("dashboard_count"));
            return vo;
        }, countQuery.args.toArray());
        if (page == null) {
            page = new DataPortalPageVO();
            page.setTotal(total);
        }

        List<Object> listArgs = new ArrayList<>();
        listArgs.add(uid);
        listArgs.addAll(query.args);
        int offset = (normalized.getPage() - 1) * normalized.getPageSize();
        String orderDirection = Boolean.TRUE.equals(normalized.getAsc()) ? "ASC" : "DESC";
        String listSql = dialect().limitOffset("""
                SELECT v.id,
                       v.name,
                       v.type,
                       v.mobile_layout AS ext_flag,
                       v.update_time,
                       COALESCE(u.name, %s) AS creator_name,
                       (SELECT COUNT(1) FROM core_chart_view c WHERE c.scene_id = v.id) AS chart_count,
                       %s AS certified,
                       %s AS recommended,
                       %s AS deprecated,
                       %s AS favorite
                """.formatted(
                dialect().stringCast("v.create_by"),
                datasetAssetFlag("p.certified = 1\n                             AND p.deprecated = 0"),
                datasetAssetFlag("p.recommended = 1\n                             AND p.deprecated = 0"),
                datasetAssetFlag("p.deprecated = 1"),
                favoriteFlag()
        ) + query.fromWhere + " ORDER BY " + orderExpression(normalized, orderDirection),
                normalized.getPageSize(), offset);
        List<DataPortalResourceVO> records = jdbcTemplate.query(listSql, (rs, rowNum) -> {
            DataPortalResourceVO vo = new DataPortalResourceVO();
            vo.setId(rs.getLong("id"));
            vo.setName(rs.getString("name"));
            vo.setType(rs.getString("type"));
            vo.setExtFlag(rs.getInt("ext_flag"));
            vo.setUpdateTime(rs.getLong("update_time"));
            vo.setCreatorName(rs.getString("creator_name"));
            vo.setChartCount(rs.getInt("chart_count"));
            vo.setFavorite(rs.getBoolean("favorite"));
            vo.setCertified(rs.getBoolean("certified"));
            vo.setRecommended(rs.getBoolean("recommended"));
            vo.setDeprecated(rs.getBoolean("deprecated"));
            return vo;
        }, listArgs.toArray());

        page.setTotal(total);
        page.setPage(normalized.getPage());
        page.setPageSize(normalized.getPageSize());
        page.setRecords(records);
        return page;
    }

    /**
     * 查询单个门户资源详情并记录最近访问
     */
    @GetMapping("/resource/{id}")
    public DataPortalResourceVO resource(@PathVariable("id") Long id) {
        if (id == null) {
            CrestException.throwException("资源不存在");
        }
        DataPortalResourceRequest request = new DataPortalResourceRequest();
        request.setPage(1);
        request.setPageSize(1);
        Long uid = dataPortalPermissionManage.currentUid();
        QueryParts query = buildBaseQuery(uid, request);
        query.fromWhere += " AND v.id = ?";
        query.args.add(id);
        List<Object> resourceArgs = new ArrayList<>();
        resourceArgs.add(uid);
        resourceArgs.addAll(query.args);
        String resourceSql = dialect().limitOne("""
                SELECT v.id,
                       v.name,
                       v.type,
                       v.mobile_layout AS ext_flag,
                       v.update_time,
                       COALESCE(u.name, %s) AS creator_name,
                       (SELECT COUNT(1) FROM core_chart_view c WHERE c.scene_id = v.id) AS chart_count,
                       %s AS certified,
                       %s AS recommended,
                       %s AS deprecated,
                       %s AS favorite
                """.formatted(
                dialect().stringCast("v.create_by"),
                datasetAssetFlag("p.certified = 1\n                             AND p.deprecated = 0"),
                datasetAssetFlag("p.recommended = 1\n                             AND p.deprecated = 0"),
                datasetAssetFlag("p.deprecated = 1"),
                favoriteFlag()
        ) + query.fromWhere);
        List<DataPortalResourceVO> resources = jdbcTemplate.query(resourceSql, (rs, rowNum) -> {
            DataPortalResourceVO vo = new DataPortalResourceVO();
            vo.setId(rs.getLong("id"));
            vo.setName(rs.getString("name"));
            vo.setType(rs.getString("type"));
            vo.setExtFlag(rs.getInt("ext_flag"));
            vo.setUpdateTime(rs.getLong("update_time"));
            vo.setCreatorName(rs.getString("creator_name"));
            vo.setChartCount(rs.getInt("chart_count"));
            vo.setFavorite(rs.getBoolean("favorite"));
            vo.setCertified(rs.getBoolean("certified"));
            vo.setRecommended(rs.getBoolean("recommended"));
            vo.setDeprecated(rs.getBoolean("deprecated"));
            return vo;
        }, resourceArgs.toArray());
        if (resources.isEmpty()) {
            CrestException.throwException("资源不存在或无访问权限");
        }
        DataPortalResourceVO resource = resources.get(0);
        recordRecent(resource);
        return resource;
    }

    /**
     * 规范化门户资源查询参数
     */
    private DataPortalResourceRequest normalizeRequest(DataPortalResourceRequest request) {
        DataPortalResourceRequest normalized = request == null ? new DataPortalResourceRequest() : request;
        int page = normalized.getPage() == null || normalized.getPage() < 1 ? 1 : normalized.getPage();
        int pageSize = normalized.getPageSize() == null || normalized.getPageSize() < 1 ? 24 : normalized.getPageSize();
        normalized.setPage(page);
        normalized.setPageSize(Math.min(pageSize, MAX_PAGE_SIZE));
        String type = StringUtils.trimToEmpty(normalized.getType());
        if (StringUtils.isBlank(type) || "all".equalsIgnoreCase(type)) {
            normalized.setType(null);
        } else {
            String normalizedType = type.toLowerCase(Locale.ROOT);
            if ("screen".equals(normalizedType)) {
                normalizedType = "datav";
            }
            if (!"datav".equals(normalizedType) && !"dashboard".equals(normalizedType)) {
                CrestException.throwException("资源类型不正确");
            }
            normalized.setType("datav".equals(normalizedType) ? "dataV" : "dashboard");
        }
        normalized.setKeyword(StringUtils.trimToNull(normalized.getKeyword()));
        String queryFrom = StringUtils.trimToEmpty(normalized.getQueryFrom()).toLowerCase(Locale.ROOT);
        if ("favorite".equals(queryFrom) || "favorites".equals(queryFrom)) {
            queryFrom = "store";
        }
        if (!"store".equals(queryFrom) && !"recent".equals(queryFrom)) {
            queryFrom = null;
        }
        normalized.setQueryFrom(queryFrom);
        return normalized;
    }

    /**
     * 构造门户资源查询的基础 SQL 和参数
     */
    private QueryParts buildBaseQuery(Long uid, DataPortalResourceRequest request) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                FROM core_visualization v
                LEFT JOIN core_iam_user u ON u.id = %s
                """.formatted(dialect().numberCast("v.create_by")));
        if (request != null && "store".equals(request.getQueryFrom())) {
            sql.append("""
                INNER JOIN core_workspace_favorite_resource wfr ON wfr.resource_id = v.id
                  AND wfr.%s = ?
                """.formatted(uidColumn()));
            args.add(uid);
        } else if (request != null && "recent".equals(request.getQueryFrom())) {
            sql.append("""
                INNER JOIN core_workspace_recent_resource wrr ON wrr.resource_id = v.id
                  AND wrr.%s = ?
                """.formatted(uidColumn()));
            args.add(uid);
        }
        sql.append("""
                WHERE v.delete_flag = 0
                  AND v.node_type = 'leaf'
                  AND v.status = 1
                  AND v.type IN ('dataV', 'dashboard')
                """);
        if (request != null && StringUtils.isNotBlank(request.getType())) {
            sql.append(" AND v.type = ?");
            args.add(request.getType());
        }
        if (request != null && StringUtils.isNotBlank(request.getKeyword())) {
            sql.append(" AND ").append(dialect().caseInsensitiveLike("v.name", "?"));
            args.add(request.getKeyword());
        }
        dataPortalPermissionManage.appendReadScope(sql, args, uid);
        return new QueryParts(sql.toString(), args);
    }

    /**
     * 复制查询条件并清空资源类型，用于统计全部类型数量
     */
    private DataPortalResourceRequest copyWithoutType(DataPortalResourceRequest source) {
        DataPortalResourceRequest copy = new DataPortalResourceRequest();
        copy.setKeyword(source.getKeyword());
        copy.setPage(source.getPage());
        copy.setPageSize(source.getPageSize());
        copy.setAsc(source.getAsc());
        copy.setQueryFrom(source.getQueryFrom());
        return copy;
    }

    /**
     * 根据查询来源生成排序表达式
     */
    private String orderExpression(DataPortalResourceRequest request, String direction) {
        if (request != null && "store".equals(request.getQueryFrom())) {
            return "wfr." + timeColumn() + " " + direction;
        }
        if (request != null && "recent".equals(request.getQueryFrom())) {
            return "wrr." + timeColumn() + " " + direction;
        }
        return "deprecated ASC, certified DESC, recommended DESC, v.update_time " + direction;
    }

    /**
     * 记录门户资源最近访问
     */
    private void recordRecent(DataPortalResourceVO resource) {
        coreOptRecentManage.saveOpt(
                resource.getId(),
                OptConstants.OPT_RESOURCE_TYPE.VISUALIZATION,
                OptConstants.OPT_TYPE.UPDATE
        );
    }

    private String datasetAssetFlag(String assetCondition) {
        return dialect().existsFlag("""
                SELECT 1
                FROM core_chart_view c
                INNER JOIN core_asset_profile p ON %s
                  AND %s
                  AND %s
                WHERE c.scene_id = v.id
                """.formatted(
                dialect().stringEquals("p.asset_type", dialect().stringLiteral("dataset")),
                dialect().stringEquals("p.asset_id", dialect().stringCast("c.table_id")),
                assetCondition
        ));
    }

    private String favoriteFlag() {
        return dialect().existsFlag("""
                SELECT 1
                FROM core_workspace_favorite_resource fav
                WHERE fav.resource_id = v.id
                  AND fav.%s = ?
                """.formatted(uidColumn()));
    }

    // 门户资源查询统一通过元数据库方言处理保留字和分页差异
    private MetadataDbDialect dialect() {
        return MetadataDbDialects.current(environment);
    }

    // UID 在 OB Oracle 中是保留字，查询拼接时需要按方言引用
    private String uidColumn() {
        return dialect().quoteIdentifier("UID");
    }

    // TIME 在部分元数据库中需要引用，避免最近访问排序 SQL 解析失败
    private String timeColumn() {
        return dialect().quoteIdentifier("TIME");
    }

    /**
     * 门户资源查询 SQL 片段和参数
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
