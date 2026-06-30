package io.crest.datasource.provider;

import com.sun.net.httpserver.HttpServer;
import io.crest.api.ds.vo.ExcelConfiguration;
import io.crest.api.ds.vo.ExcelFileData;
import io.crest.api.ds.vo.ExcelSheetData;
import io.crest.extensions.datasource.dto.DatasourceDTO;
import io.crest.extensions.datasource.dto.DatasourceRequest;
import io.crest.utils.JsonUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 覆盖 Excel 数据源文件在共享目录中的保存和读取行为。
 */
class ExcelUtilsStorageTest {

    @TempDir
    Path excelRoot;

    @AfterEach
    void tearDown() {
        System.clearProperty("crest.path.excel");
        System.clearProperty("crest.security.remote-download.allow-private-address");
    }

    @Test
    @DisplayName("上传 Excel 数据源文件应写入配置的共享目录并保持历史 path 可读")
    void excelUploadShouldWriteToConfiguredStoragePathAndReadSavedData() throws Exception {
        System.setProperty("crest.path.excel", excelRoot.toString() + File.separator);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sales.csv",
                "text/csv",
                "name,count\nalpha,3\n".getBytes(StandardCharsets.UTF_8)
        );

        ExcelFileData excelFileData = new ExcelUtils().excelSaveAndParse(file, "1");

        Path savedPath = Path.of(excelFileData.getPath()).toRealPath().normalize();
        assertTrue(savedPath.startsWith(excelRoot.toRealPath().normalize()));
        assertEquals("name,count\nalpha,3\n", Files.readString(savedPath));

        ExcelSheetData sheet = excelFileData.getSheets().get(0);
        DatasourceRequest request = new DatasourceRequest();
        DatasourceDTO datasource = new DatasourceDTO();
        datasource.setType("Excel");
        datasource.setConfiguration(JsonUtil.toJSONString(List.of(sheet)).toString());
        request.setDatasource(datasource);
        request.setTable(sheet.getDisplayTableName());

        List<String[]> rows = new ExcelUtils().fetchDataList(request);

        assertEquals(1, rows.size());
        assertArrayEquals(new String[]{"alpha", "3"}, rows.get(0));
    }

    @Test
    @DisplayName("HTTP 远程 Excel 下载应写入配置的共享目录")
    void remoteHttpExcelDownloadShouldWriteToConfiguredStoragePath() throws Exception {
        System.setProperty("crest.path.excel", excelRoot.toString() + File.separator);
        System.setProperty("crest.security.remote-download.allow-private-address", "true");
        byte[] body = "name,count\nbeta,5\n".getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sales.csv", exchange -> {
            exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"sales.csv\"");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();
        try {
            ExcelConfiguration configuration = new ExcelConfiguration();
            configuration.setUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/sales.csv");

            Map<String, String> fileNames = downloadRemoteExcel(configuration);

            assertEquals("sales.csv", fileNames.get("fileName"));
            Path savedPath = Path.of(ExcelUtils.getExcelPath(), fileNames.get("tranName")).toRealPath().normalize();
            assertTrue(savedPath.startsWith(excelRoot.toRealPath().normalize()));
            assertEquals("name,count\nbeta,5\n", Files.readString(savedPath));
        } finally {
            server.stop(0);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> downloadRemoteExcel(ExcelConfiguration configuration) throws Exception {
        Method method = ExcelUtils.class.getDeclaredMethod("downLoadRemoteExcel", ExcelConfiguration.class);
        method.setAccessible(true);
        return (Map<String, String>) method.invoke(null, configuration);
    }
}
