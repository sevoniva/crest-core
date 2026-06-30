package io.crest.datasource.server;

import io.crest.datasource.dao.ext.mapper.DataSourceExtMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataSourceExtMapperSqlTest {

    @Test
    void selectTimestampUsesObOracleCompatibleEpochSecondsSql() throws NoSuchMethodException {
        String sql = Arrays.stream(DataSourceExtMapper.class.getMethod("selectTimestamp").getAnnotations())
                .filter(annotation -> annotation.annotationType().getName().equals("org.apache.ibatis.annotations.Select"))
                .findFirst()
                .map(annotation -> {
                    try {
                        return String.join(" ", (String[]) annotation.annotationType().getMethod("value").invoke(annotation));
                    } catch (ReflectiveOperationException e) {
                        throw new AssertionError(e);
                    }
                })
                .orElseThrow();

        assertFalse(sql.toLowerCase().contains("unix_timestamp"));
        assertTrue(sql.contains("SYS_EXTRACT_UTC"));
        assertTrue(sql.contains("SYSTIMESTAMP"));
        assertTrue(sql.contains("FROM DUAL"));
    }
}
