package io.crest.datasource.provider;

import io.crest.datasource.dao.auto.entity.CoreEngine;
import io.crest.datasource.server.DatasourceServer;
import io.crest.dataset.utils.TableUtils;
import io.crest.extensions.datasource.dto.TableField;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 提供 OceanBase Oracle 模式下抽取引擎的建表、写入和表替换 SQL 生成能力
 */
@Service("obOracleEngine")
public class ObOracleEngineProvider extends EngineProvider {

    @Override
    public String createView(String name, String viewSQL) {
        validateSqlInjectionRisk(name);
        return "CREATE OR REPLACE VIEW " + quote(name) + " AS " + viewSQL;
    }

    @Override
    public String dropTable(String name) {
        validateSqlInjectionRisk(name);
        return "DROP TABLE " + quote(name) + " PURGE";
    }

    @Override
    public String dropView(String name) {
        validateSqlInjectionRisk(name);
        return "DROP VIEW " + quote(name);
    }

    @Override
    public String replaceTable(String name) {
        validateSqlInjectionRisk(name);
        String tmpName = TableUtils.tmpName(name);
        String oldName = TableUtils.tmpName("old_" + name);
        return "DROP TABLE " + quote(oldName) + " PURGE; "
                + "ALTER TABLE " + quote(name) + " RENAME TO " + quote(oldName) + "; "
                + "ALTER TABLE " + quote(tmpName) + " RENAME TO " + quote(name) + "; "
                + "DROP TABLE " + quote(oldName) + " PURGE";
    }

    @Override
    public String createTableSql(String name, List<TableField> tableFields, CoreEngine engine) {
        validateSqlInjectionRisk(name);
        return "CREATE TABLE " + quote(name) + " (" + tableFields.stream()
                .filter(TableField::isChecked)
                .map(this::columnDefinition)
                .collect(Collectors.joining(", "))
                + primaryKey(tableFields) + ")";
    }

    @Override
    public String insertSql(String dsType, String tableName, DatasourceServer.UpdateType extractType,
                            List<String[]> dataList, int page, int pageNumber, List<TableField> tableFields) {
        String engineTableName = switch (extractType) {
            case all_scope -> TableUtils.tmpName(TableUtils.tableName(tableName));
            default -> TableUtils.tableName(tableName);
        };
        List<TableField> checkedFields = tableFields.stream().filter(TableField::isChecked).toList();
        String columns = checkedFields.stream()
                .map(field -> quote(physicalFieldName(field)))
                .collect(Collectors.joining(", "));
        int realSize = Math.min(page * pageNumber, dataList.size());
        String rows = dataList.subList((page - 1) * pageNumber, realSize).stream()
                .map(row -> selectRow(row, checkedFields, tableFields))
                .collect(Collectors.joining(" UNION ALL "));
        return "INSERT INTO " + quote(engineTableName) + " (" + columns + ") " + rows;
    }

    private String columnDefinition(TableField field) {
        String fieldName = physicalFieldName(field);
        validateSqlInjectionRisk(fieldName);
        return quote(fieldName) + " " + columnType(field);
    }

    private String columnType(TableField field) {
        return switch (field.getExtractedFieldType()) {
            case 1 -> "TIMESTAMP";
            case 2 -> "NUMBER(19,0)";
            case 3 -> "NUMBER(27,8)";
            case 4 -> "NUMBER(1,0)";
            default -> "VARCHAR2(" + varcharLength(field.getLength()) + ")";
        };
    }

    private int varcharLength(String length) {
        String normalized = StringUtils.trimToEmpty(length);
        if (!StringUtils.isNumeric(normalized)) {
            return 4000;
        }
        try {
            long parsed = Long.parseLong(normalized);
            return (int) Math.min(Math.max(parsed, 1), 4000);
        } catch (NumberFormatException e) {
            return 4000;
        }
    }

    private String primaryKey(List<TableField> tableFields) {
        String keys = tableFields.stream()
                .filter(TableField::isChecked)
                .filter(TableField::isPrimaryKey)
                .map(field -> quote(physicalFieldName(field)))
                .collect(Collectors.joining(", "));
        if (StringUtils.isBlank(keys)) {
            return "";
        }
        return ", PRIMARY KEY (" + keys + ")";
    }

    private String selectRow(String[] row, List<TableField> checkedFields, List<TableField> tableFields) {
        String[] values = new String[checkedFields.size()];
        int valueIndex = 0;
        for (int i = 0; i < row.length && i < tableFields.size(); i++) {
            TableField tableField = tableFields.get(i);
            if (tableField.isChecked()) {
                values[valueIndex] = sqlLiteral(row[i], tableField);
                valueIndex++;
            }
        }
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) {
                values[i] = "NULL";
            }
        }
        return "SELECT " + String.join(", ", Arrays.asList(values)) + " FROM DUAL";
    }

    private String sqlLiteral(String value, TableField field) {
        if (StringUtils.isBlank(value)) {
            return "NULL";
        }
        String trimmed = value.trim();
        return switch (field.getExtractedFieldType()) {
            case 1 -> "TO_TIMESTAMP('" + escape(trimmed) + "', 'YYYY-MM-DD HH24:MI:SS.FF')";
            case 2, 3 -> isNumber(trimmed) ? trimmed : "NULL";
            case 4 -> booleanLiteral(trimmed);
            default -> "'" + escape(value) + "'";
        };
    }

    private String booleanLiteral(String value) {
        if (Strings.CI.equalsAny(value, "true", "yes")) {
            return "1";
        }
        if (Strings.CI.equalsAny(value, "false", "no")) {
            return "0";
        }
        return isNumber(value) ? value : "NULL";
    }

    private boolean isNumber(String value) {
        return StringUtils.defaultString(value).matches("-?\\d+(\\.\\d+)?");
    }

    private String escape(String value) {
        return StringUtils.defaultString(value).replace("'", "''");
    }

    private String quote(String value) {
        return "\"" + value + "\"";
    }
}
