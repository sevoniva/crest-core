package io.crest.visualization.server;

import io.crest.api.visualization.request.StaticResourceRequest;
import io.crest.storage.LocalStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StaticResourceStorageTest {

    private static final String SVG_BASE64 = "PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxIiBoZWlnaHQ9IjEiPjwvc3ZnPg==";

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("静态资源保存应写入存储服务目录")
    void saveShouldWriteStaticResourceToStorageService() throws Exception {
        StaticResourceServer server = staticResourceServer();

        server.saveSingleFileToServe("sample.svg", SVG_BASE64);

        assertThat(tempDir.resolve("sample.svg")).exists();
        assertThat(Files.readAllBytes(tempDir.resolve("sample.svg"))).isNotEmpty();
    }

    @Test
    @DisplayName("静态资源读取应通过存储服务返回 Base64")
    void resourceBase64ShouldReadFromStorageService() {
        StaticResourceServer server = staticResourceServer();
        server.saveSingleFileToServe("sample.svg", SVG_BASE64);
        StaticResourceRequest request = new StaticResourceRequest();
        request.setResourcePathList(List.of("/static-resource/sample.svg"));

        Map<String, String> result = server.resourceBase64(request);

        assertThat(result).containsEntry("/static-resource/sample.svg", SVG_BASE64);
    }

    @Test
    @DisplayName("模板文件应接受 .crest 后缀")
    void crestTemplateFileExtensionShouldBeAcceptedCaseInsensitively() {
        assertThat(DataVisualizationServer.isCrestTemplateFile("sales.crest")).isTrue();
        assertThat(DataVisualizationServer.isCrestTemplateFile("sales.CREST")).isTrue();
    }

    @Test
    @DisplayName("模板文件应拒绝旧后缀")
    void legacyTemplateFileExtensionsShouldBeRejected() {
        assertThat(DataVisualizationServer.isCrestTemplateFile("sales.DET2")).isFalse();
        assertThat(DataVisualizationServer.isCrestTemplateFile("sales.DET2APP")).isFalse();
        assertThat(DataVisualizationServer.isCrestTemplateFile("sales.json")).isFalse();
        assertThat(DataVisualizationServer.isCrestTemplateFile(null)).isFalse();
    }

    private StaticResourceServer staticResourceServer() {
        StaticResourceServer server = new StaticResourceServer();
        ReflectionTestUtils.setField(server, "staticDir", tempDir.toString());
        ReflectionTestUtils.setField(server, "storageService", new LocalStorageService());
        return server;
    }
}
