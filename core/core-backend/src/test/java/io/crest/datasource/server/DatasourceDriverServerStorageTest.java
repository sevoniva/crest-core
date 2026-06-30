package io.crest.datasource.server;

import io.crest.datasource.dao.auto.entity.CoreDriver;
import io.crest.datasource.dao.auto.entity.CoreDriverJar;
import io.crest.datasource.dao.auto.mapper.CoreDriverJarMapper;
import io.crest.datasource.dao.auto.mapper.CoreDriverMapper;
import io.crest.storage.LocalStorageService;
import io.crest.utils.Md5Utils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatasourceDriverServerStorageTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("驱动上传应通过存储服务写入共享目录")
    void uploadJarShouldWriteDriverJarToStorageService() throws Exception {
        DatasourceDriverServer server = server();
        CoreDriverMapper driverMapper = driverMapper(server);
        CoreDriverJarMapper jarMapper = jarMapper(server);
        CoreDriver driver = new CoreDriver();
        driver.setId(12L);
        when(driverMapper.selectById("12")).thenReturn(driver);
        when(jarMapper.selectList(any())).thenReturn(List.of());
        MockMultipartFile file = new MockMultipartFile("jarFile", "custom-driver.jar", "application/java-archive", "driver-bytes".getBytes());

        server.uploadJar("12", file);

        String transName = Md5Utils.md5("custom-driver.jar") + ".jar";
        assertThat(tempDir.resolve("12").resolve(transName)).hasContent("driver-bytes");
        ArgumentCaptor<CoreDriverJar> captor = ArgumentCaptor.forClass(CoreDriverJar.class);
        verify(jarMapper).insert(captor.capture());
        assertThat(captor.getValue().getFileName()).isEqualTo("custom-driver.jar");
        assertThat(captor.getValue().getTransName()).isEqualTo(transName);
    }

    @Test
    @DisplayName("驱动删除应通过存储服务移除共享目录文件")
    void deleteDriverJarShouldRemoveDriverJarFromStorageService() throws Exception {
        DatasourceDriverServer server = server();
        CoreDriverJarMapper jarMapper = jarMapper(server);
        Path driverDir = Files.createDirectories(tempDir.resolve("12"));
        Files.writeString(driverDir.resolve("custom.jar"), "driver-bytes");
        CoreDriverJar driverJar = new CoreDriverJar();
        driverJar.setDriverId("12");
        driverJar.setTransName("custom.jar");
        when(jarMapper.selectById("99")).thenReturn(driverJar);

        server.deleteDriverJar("99");

        assertThat(driverDir.resolve("custom.jar")).doesNotExist();
        verify(jarMapper).deleteById("99");
    }

    private DatasourceDriverServer server() {
        DatasourceDriverServer server = new DatasourceDriverServer();
        ReflectionTestUtils.setField(server, "DRIVER_PATH", tempDir.toString());
        ReflectionTestUtils.setField(server, "coreDriverMapper", mock(CoreDriverMapper.class));
        ReflectionTestUtils.setField(server, "coreDriverJarMapper", mock(CoreDriverJarMapper.class));
        ReflectionTestUtils.setField(server, "storageService", new LocalStorageService());
        return server;
    }

    private CoreDriverMapper driverMapper(DatasourceDriverServer server) {
        return (CoreDriverMapper) ReflectionTestUtils.getField(server, "coreDriverMapper");
    }

    private CoreDriverJarMapper jarMapper(DatasourceDriverServer server) {
        return (CoreDriverJarMapper) ReflectionTestUtils.getField(server, "coreDriverJarMapper");
    }
}
