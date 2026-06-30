package io.crest.log;

import io.crest.auth.CrestPermit;
import io.crest.constant.LogOT;
import io.crest.constant.LogST;
import io.crest.exception.CrestException;
import io.crest.metadata.MetadataDbDialect;
import io.crest.metadata.MetadataDbDialects;
import io.crest.result.ResultCode;
import io.crest.substitute.permissions.auth.PlatformPermissionManage;
import io.crest.utils.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 审计日志控制器
 *
 * 提供审计日志查询接口
 */
@Tag(name = "审计日志")
@RestController
@RequestMapping("/audit-log")
public class AuditLogController {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 15;
    private static final int MAX_PAGE_SIZE = 200;
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String LIST_COLUMNS = """
            id, operation_type, resource_type, resource_id, resource_name,
            operation_desc, request_method, request_url, response_code, response_msg,
            operator_id, operator_name, operator_account, operator_ip, operation_time, duration
            """.replaceAll("\\s+", " ").trim();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformPermissionManage platformPermissionManage;

    @Autowired
    private Environment environment;

    @CrestAudit(ot = LogOT.READ, st = LogST.DATA)
    @Operation(summary = "查询审计日志")
    @CrestPermit("m:read")
    @PostMapping("/page/{goPage}/{pageSize}")
    public Map<String, Object> pager(
            @PathVariable("goPage") int goPage,
            @PathVariable("pageSize") int pageSize,
            @RequestBody(required = false) Map<String, Object> request) {

        requireAuditAccess();
        int safePage = Math.max(goPage, DEFAULT_PAGE);
        int safePageSize = pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        int offset = (safePage - 1) * safePageSize;
        MetadataDbDialect metadataDialect = dialect();

        // 构建查询条件
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new java.util.ArrayList<>();

        if (request != null) {
            String operationType = (String) request.get("operationType");
            if (operationType != null && !operationType.isEmpty()) {
                where.append(" AND operation_type = ?");
                params.add(operationType);
            }

            String resourceType = (String) request.get("resourceType");
            if (resourceType != null && !resourceType.isEmpty()) {
                where.append(" AND resource_type = ?");
                params.add(resourceType);
            }

            String operatorAccount = (String) request.get("operatorAccount");
            if (operatorAccount != null && !operatorAccount.isEmpty()) {
                where.append(" AND operator_account LIKE ?");
                params.add("%" + operatorAccount + "%");
            }

            String startTime = (String) request.get("startTime");
            if (startTime != null && !startTime.isEmpty()) {
                where.append(" AND operation_time >= ").append(metadataDialect.timestampParameter());
                params.add(parseTimestamp(metadataDialect, startTime));
            }

            String endTime = (String) request.get("endTime");
            if (endTime != null && !endTime.isEmpty()) {
                where.append(" AND operation_time <= ").append(metadataDialect.timestampParameter());
                params.add(parseTimestamp(metadataDialect, endTime));
            }
        }

        // 查询总数
        String countSql = "SELECT COUNT(*) FROM core_audit_log" + where;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());

        String dataSql = metadataDialect.limitOffset(
                "SELECT " + LIST_COLUMNS + " FROM core_audit_log" + where + " ORDER BY operation_time DESC",
                safePageSize,
                offset);
        List<Map<String, Object>> records = normalizeRows(jdbcTemplate.queryForList(dataSql, params.toArray()));

        Map<String, Object> result = new HashMap<>();
        result.put("total", total != null ? total : 0);
        result.put("records", records);
        result.put("current", safePage);
        result.put("size", safePageSize);

        return result;
    }

    @Operation(summary = "审计日志统计")
    @CrestPermit("m:read")
    @GetMapping("/statistics")
    public Map<String, Object> statistics() {
        requireAuditAccess();
        MetadataDbDialect metadataDialect = dialect();
        Map<String, Object> stats = new HashMap<>();
        Object startOfDay = timestampValue(metadataDialect, LocalDate.now(ZoneId.systemDefault()).atStartOfDay());
        Object startOfNextDay = timestampValue(
                metadataDialect,
                LocalDate.now(ZoneId.systemDefault()).plusDays(1).atStartOfDay());
        String dayStartExpression = metadataDialect.timestampParameter();
        String dayEndExpression = metadataDialect.timestampParameter();

        // 今日操作数
        Long todayCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM core_audit_log WHERE operation_time >= " + dayStartExpression
                        + " AND operation_time < " + dayEndExpression,
                Long.class, startOfDay, startOfNextDay);
        stats.put("todayCount", todayCount != null ? todayCount : 0);

        // 今日登录数
        Long todayLogin = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM core_audit_log WHERE operation_type = 'LOGIN' AND operation_time >= "
                        + dayStartExpression + " AND operation_time < " + dayEndExpression,
                Long.class, startOfDay, startOfNextDay);
        stats.put("todayLogin", todayLogin != null ? todayLogin : 0);

        // 操作类型统计
        List<Map<String, Object>> typeStats = jdbcTemplate.queryForList(
                "SELECT operation_type, COUNT(*) as count FROM core_audit_log WHERE operation_time >= "
                        + dayStartExpression + " AND operation_time < " + dayEndExpression
                        + " GROUP BY operation_type",
                startOfDay, startOfNextDay);
        stats.put("typeStats", normalizeRows(typeStats));

        return stats;
    }

    @Operation(summary = "获取操作类型列表")
    @CrestPermit("m:read")
    @GetMapping("/operation-types")
    public List<Map<String, Object>> operationTypes() {
        requireAuditAccess();
        return normalizeRows(jdbcTemplate.queryForList(
                "SELECT DISTINCT operation_type as value, operation_type as label FROM core_audit_log ORDER BY operation_type"));
    }

    // 审计日志只允许系统管理员和审计员访问
    private void requireAuditAccess() {
        Long uid = AuthUtils.getUser() == null ? null : AuthUtils.getUser().getUserId();
        if (uid == null) {
            CrestException.throwException(ResultCode.PERMISSION_NO_ACCESS.code(), "当前用户无权访问审计日志");
        }
        if (platformPermissionManage.isSystemAdmin(uid)) {
            return;
        }
        boolean hasAuditRole = platformPermissionManage.roleIds(uid, platformPermissionManage.defaultOrgId(uid))
                .contains(PlatformPermissionManage.AUDITOR_ROLE_ID);
        if (!hasAuditRole) {
            CrestException.throwException(ResultCode.PERMISSION_NO_ACCESS.code(), "当前用户无权访问审计日志");
        }
    }

    // 审计筛选 SQL 按当前系统库方言生成分页和函数片段
    private MetadataDbDialect dialect() {
        return MetadataDbDialects.current(environment);
    }

    private Object parseTimestamp(MetadataDbDialect metadataDialect, String value) {
        try {
            return metadataDialect.timestampValue(value);
        } catch (IllegalArgumentException e) {
            CrestException.throwException(ResultCode.PARAM_IS_INVALID.code(), "时间格式必须为 yyyy-MM-dd HH:mm:ss");
            return null;
        }
    }

    private Object timestampValue(MetadataDbDialect metadataDialect, LocalDateTime value) {
        return metadataDialect.timestampValue(value.format(TIMESTAMP_FORMATTER));
    }

    private List<Map<String, Object>> normalizeRows(List<Map<String, Object>> rows) {
        return rows.stream().map(this::normalizeRow).toList();
    }

    private Map<String, Object> normalizeRow(Map<String, Object> row) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        row.forEach((key, value) -> normalized.put(key.toLowerCase(Locale.ROOT), value));
        return normalized;
    }
}
