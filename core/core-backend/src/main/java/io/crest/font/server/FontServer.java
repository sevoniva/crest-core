package io.crest.font.server;

import io.crest.api.font.api.FontApi;
import io.crest.api.font.dto.FontDto;
import io.crest.exception.CrestException;
import io.crest.font.manage.FontManage;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 提供字体资源的查询、维护和文件传输接口
 */
@RestController
@RequestMapping("/typeface")
public class FontServer implements FontApi {

    @Resource
    private FontManage fontManage;

    @Value("${crest.feature.font-management.enabled:false}")
    private boolean fontManagementEnabled;

    /**
     * 查询所有字体资源
     */
    @Override
    public List<FontDto> list() {
        return fontManage.list();
    }

    /**
     * 创建字体资源记录
     */
    @Override
    public FontDto create(FontDto fontDto) {
        assertFontManagementEnabled();
        return fontManage.create(fontDto);
    }

    /**
     * 编辑字体资源记录
     */
    @Override
    public FontDto edit(FontDto fontDto) {
        assertFontManagementEnabled();
        return fontManage.edit(fontDto);
    }

    /**
     * 删除指定字体资源
     */
    @Override
    public void delete(Long id) {
        assertFontManagementEnabled();
        fontManage.delete(id);
    }

    /**
     * 切换默认字体资源
     */
    @Override
    public void changeDefault(FontDto fontDto) {
        assertFontManagementEnabled();
        fontManage.changeDefault(fontDto);
    }

    /**
     * 上传字体文件并创建字体资源
     */
    @Override
    public FontDto upload(MultipartFile file) throws CrestException {
        assertFontManagementEnabled();
        return fontManage.upload(file);
    }

    /**
     * 下载指定字体文件
     */
    @Override
    public void download(String file, HttpServletResponse response) throws CrestException {
        fontManage.download(file, response);
    }

    /**
     * 查询系统默认字体列表
     */
    @Override
    public List<FontDto> defaultFont() throws CrestException {
        return fontManage.defaultFont();
    }

    private void assertFontManagementEnabled() {
        if (!fontManagementEnabled) {
            CrestException.throwException("当前版本未启用字体自定义管理");
        }
    }
}
