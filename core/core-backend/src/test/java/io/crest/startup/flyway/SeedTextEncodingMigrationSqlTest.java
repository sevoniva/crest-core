package io.crest.startup.flyway;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeedTextEncodingMigrationSqlTest {

    @Test
    void repairUserTextEncodingMigrationFixesMojibakeAdminName() throws IOException {
        String sql = readClasspathResource("db/migration-ob-oracle/V1.0.0.13__repair_user_text_encoding.sql");

        assertTrue(sql.contains("UPDATE core_iam_user"));
        assertTrue(sql.contains("SET name = UNISTR('\\7BA1\\7406\\5458')"));
        assertTrue(sql.contains("account = 'admin'"));
        assertTrue(sql.contains("name = UNISTR('\\00E7\\00AE\\00A1\\00E7\\0090\\2020\\00E5\\2018\\02DC')"));
    }

    private static String readClasspathResource(String path) throws IOException {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            assertNotNull(inputStream, path + " must exist on the test classpath");
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
