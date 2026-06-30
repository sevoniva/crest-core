package io.crest.font.manage;

import io.crest.font.dao.auto.entity.CoreFont;
import io.crest.font.dao.auto.mapper.CoreFontMapper;
import io.crest.storage.LocalStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FontManageStorageTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("字体下载应通过存储服务读取共享目录文件")
    void downloadShouldReadFontFromStorageService() throws Exception {
        FontManage manage = fontManage();
        CoreFont font = font("custom.ttf");
        Files.writeString(tempDir.resolve("custom.ttf"), "font-content");
        when(mapper(manage).selectList(any())).thenReturn(List.of(font));
        MockHttpServletResponse response = new MockHttpServletResponse();

        manage.download("custom.ttf", response);

        assertThat(response.getContentAsString()).isEqualTo("font-content");
        assertThat(response.getHeader("Content-Disposition")).contains("custom.ttf");
    }

    @Test
    @DisplayName("字体删除应通过存储服务删除共享目录文件")
    void deleteShouldRemoveFontFromStorageService() throws Exception {
        FontManage manage = fontManage();
        CoreFontMapper mapper = mapper(manage);
        CoreFont font = font("remove.ttf");
        Files.writeString(tempDir.resolve("remove.ttf"), "font-content");
        when(mapper.selectById(1L)).thenReturn(font);

        manage.delete(1L);

        assertThat(tempDir.resolve("remove.ttf")).doesNotExist();
        verify(mapper).deleteById(1L);
    }

    private FontManage fontManage() {
        FontManage manage = new FontManage();
        ReflectionTestUtils.setField(manage, "path", tempDir.toString());
        ReflectionTestUtils.setField(manage, "coreFontMapper", mock(CoreFontMapper.class));
        ReflectionTestUtils.setField(manage, "storageService", new LocalStorageService());
        return manage;
    }

    private CoreFontMapper mapper(FontManage manage) {
        return (CoreFontMapper) ReflectionTestUtils.getField(manage, "coreFontMapper");
    }

    private CoreFont font(String fileName) {
        CoreFont font = new CoreFont();
        font.setId(1L);
        font.setFileTransName(fileName);
        return font;
    }
}
