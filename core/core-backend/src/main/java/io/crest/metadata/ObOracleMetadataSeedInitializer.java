package io.crest.metadata;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.crest.utils.LogUtil;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 通过预编译批处理加载 OceanBase Oracle 元数据库种子数据
 */
@Component
@Order(1)
public class ObOracleMetadataSeedInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private static final TypeReference<List<Object>> ROW_TYPE = new TypeReference<>() {
    };

    private final Environment environment;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ObOracleMetadataSeedInitializer(Environment environment, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.environment = environment;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    // 应用就绪后再补种子数据，确保 Flyway 或外部初始化已完成表结构创建
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (environment.getProperty("crest.production-mode", Boolean.class, false)) {
            return;
        }
        if (MetadataDbTypeResolver.resolve(environment) != MetadataDbType.OB_ORACLE) {
            return;
        }
        try {
            loadRefArea();
            loadVisualizationSubject();
        } catch (Exception e) {
            LogUtil.error("=====OceanBase Oracle metadata seed initialization failed=====", e);
            throw new IllegalStateException("OceanBase Oracle metadata seed initialization failed", e);
        }
    }

    // 行政区划数据量较大，使用 JSONL 加批处理避免在 Oracle SQL 文件中维护超长 INSERT
    private void loadRefArea() throws IOException {
        List<List<Object>> rows = loadRows("db/seed-ob-oracle/core_reference_area.jsonl", 4);
        Set<String> existingIds = existingIds("core_reference_area");
        List<List<Object>> missingRows = rows.stream()
                .filter(row -> !existingIds.contains(stringValue(row.get(0))))
                .toList();
        if (missingRows.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO core_reference_area (id, "LEVEL", name, pid)
                VALUES (?, ?, ?, ?)
                """, new BatchPreparedStatementSetter() {
            // 按 core_reference_area 固定列序写入缺失行
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                List<Object> row = missingRows.get(i);
                ps.setString(1, stringValue(row.get(0)));
                ps.setString(2, stringValue(row.get(1)));
                ps.setString(3, stringValue(row.get(2)));
                ps.setString(4, stringValue(row.get(3)));
            }

            // 批大小与去重后的缺失行数保持一致
            @Override
            public int getBatchSize() {
                return missingRows.size();
            }
        });
        LogUtil.info("=====OceanBase Oracle core_reference_area seed loaded: " + missingRows.size() + " rows=====");
    }

    // 内置主题包含长文本和图片字段，使用 CLOB 绑定避开 SQL 字面量长度限制
    private void loadVisualizationSubject() throws IOException {
        List<List<Object>> rows = loadRows("db/seed-ob-oracle/core_visualization_theme.jsonl", 13);
        Set<String> existingIds = existingIds("core_visualization_theme");
        List<List<Object>> missingRows = rows.stream()
                .filter(row -> !existingIds.contains(stringValue(row.get(0))))
                .toList();
        if (missingRows.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO core_visualization_theme (
                    id, name, type, details, delete_flag, cover_url, create_num,
                    create_time, create_by, update_time, update_by, delete_time, delete_by
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, new BatchPreparedStatementSetter() {
            // 按 core_visualization_theme 固定列序绑定主题字段
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                List<Object> row = missingRows.get(i);
                ps.setString(1, stringValue(row.get(0)));
                ps.setString(2, stringValue(row.get(1)));
                ps.setString(3, stringValue(row.get(2)));
                setClob(ps, 4, stringValue(row.get(3)));
                setInteger(ps, 5, integerValue(row.get(4)));
                setClob(ps, 6, stringValue(row.get(5)));
                setInteger(ps, 7, integerValue(row.get(6)));
                setLong(ps, 8, longValue(row.get(7)));
                ps.setString(9, stringValue(row.get(8)));
                setLong(ps, 10, longValue(row.get(9)));
                ps.setString(11, stringValue(row.get(10)));
                setLong(ps, 12, longValue(row.get(11)));
                setLong(ps, 13, longValue(row.get(12)));
            }

            // 批大小与主题缺失行数保持一致
            @Override
            public int getBatchSize() {
                return missingRows.size();
            }
        });
        LogUtil.info("=====OceanBase Oracle core_visualization_theme seed loaded: " + missingRows.size() + " rows=====");
    }

    // 逐行解析 JSON 数组，并校验列数防止种子文件和写入语句漂移
    private List<List<Object>> loadRows(String path, int expectedColumns) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            List<List<Object>> rows = reader.lines()
                    .filter(line -> !line.isBlank())
                    .map(this::readRow)
                    .toList();
            for (List<Object> row : rows) {
                if (row.size() != expectedColumns) {
                    throw new IllegalStateException(path + " contains a row with " + row.size() + " columns");
                }
            }
            return rows;
        }
    }

    // 单行 JSONL 必须是数组，失败时保留统一的初始化异常上下文
    private List<Object> readRow(String line) {
        try {
            return objectMapper.readValue(line, ROW_TYPE);
        } catch (IOException e) {
            throw new IllegalStateException("Invalid OceanBase Oracle seed row", e);
        }
    }

    // 读取目标表已有主键，种子加载只补缺失数据
    private Set<String> existingIds(String tableName) {
        return new HashSet<>(jdbcTemplate.queryForList("SELECT id FROM " + tableName, String.class));
    }

    // 长文本字段通过 CharacterStream 写入，兼容 OceanBase Oracle 的 CLOB 绑定
    private static void setClob(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.CLOB);
            return;
        }
        ps.setCharacterStream(index, new StringReader(value), value.length());
    }

    // JSON 数字可能由 Jackson 解析为不同 Number 子类，写入前统一收敛为 Integer
    private static void setInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
            return;
        }
        ps.setInt(index, value);
    }

    // 时间戳和主键字段统一按 BIGINT 绑定，避免 Oracle 数字精度推断差异
    private static void setLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BIGINT);
            return;
        }
        ps.setLong(index, value);
    }

    // JSON 空值保留为空，非空值按字符串写入字符列
    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    // 支持数字节点和字符串节点两种种子表示
    private static Integer integerValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }

    // 支持 Jackson 数字节点和字符串形式的长整型值
    private static Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }
}
