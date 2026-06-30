package io.crest.font.manage;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.crest.api.font.dto.FontDto;
import io.crest.exception.CrestException;
import io.crest.font.dao.auto.entity.CoreFont;
import io.crest.font.dao.auto.mapper.CoreFontMapper;
import io.crest.storage.StorageService;
import io.crest.utils.BeanUtils;
import io.crest.utils.FileUtils;
import io.crest.utils.IDUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
/**
 * 字体管理服务，负责字体元数据维护和字体文件上传下载
 */
public class FontManage {

    @Value("${crest.path.font:/opt/crest/data/font/}")
    private String path;

    @Resource
    private CoreFontMapper coreFontMapper;
    @Autowired
    private ResourceLoader resourceLoader;
    @Resource
    private StorageService storageService;

    /**
     * 查询全部字体配置
     */
    public List<FontDto> list() {
        QueryWrapper<CoreFont> queryWrapper = new QueryWrapper<>();
        List<CoreFont> coreFonts = coreFontMapper.selectList(queryWrapper);
        List<FontDto> fontDtos = new ArrayList<>();
        for (CoreFont coreFont : coreFonts) {
            FontDto dto = new FontDto();
            BeanUtils.copyBean(dto, coreFont);
            fontDtos.add(dto);
        }

        return fontDtos;
    }

    /**
     * 创建字体元数据
     */
    public FontDto create(FontDto fontDto) {
        QueryWrapper<CoreFont> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("name", fontDto.getName());
        if (CollectionUtils.isNotEmpty(coreFontMapper.selectList(queryWrapper))) {
            CrestException.throwException("存在重名字库");
        }
        fontDto.setId(IDUtils.snowID());
        CoreFont coreFont = new CoreFont();
        BeanUtils.copyBean(coreFont, fontDto);
        coreFont.setUpdateTime(System.currentTimeMillis());
        coreFontMapper.insert(coreFont);
        return fontDto;
    }


    /**
     * 编辑字体元数据，默认字体会同步清理其他默认标记
     */
    public FontDto edit(FontDto fontDto) {
        if (ObjectUtils.isEmpty(fontDto.getId())) {
            return create(fontDto);
        }
        if (fontDto.getIsDefault()) {
            UpdateWrapper<CoreFont> updateWrapper = new UpdateWrapper<>();
            updateWrapper.ne("id", fontDto.getId());
            CoreFont record = new CoreFont();
            record.setIsDefault(false);
            coreFontMapper.update(record, updateWrapper);
        }
        CoreFont coreFont = new CoreFont();
        BeanUtils.copyBean(coreFont, fontDto);
        coreFont.setUpdateTime(System.currentTimeMillis());
        coreFontMapper.updateById(coreFont);
        return fontDto;
    }

    /**
     * 删除字体元数据和对应字体文件
     */
    public void delete(Long id) {
        CoreFont coreFont = coreFontMapper.selectById(id);
        if (coreFont != null) {
            coreFontMapper.deleteById(id);
            if (StringUtils.isNotEmpty(coreFont.getFileTransName())) {
                storageService.deleteFile(path, coreFont.getFileTransName());
            }
        }

    }

    /**
     * 更新字体默认状态
     */
    public void changeDefault(FontDto fontDto) {
        QueryWrapper<CoreFont> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", fontDto.getId());
        CoreFont record = new CoreFont();
        record.setIsDefault(fontDto.getIsDefault());
        coreFontMapper.update(record, queryWrapper);
    }

    /**
     * 上传字体文件并返回字体信息
     */
    public FontDto upload(MultipartFile file) {
        String fileUuid = UUID.randomUUID().toString();
        return saveFile(file, fileUuid);
    }

    /**
     * 下载指定转换文件名的字体文件
     */
    public void download(String file, HttpServletResponse response) {

        QueryWrapper<CoreFont> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("file_trans_name", file);
        List<CoreFont> coreFonts = coreFontMapper.selectList(queryWrapper);
        if (CollectionUtils.isEmpty(coreFonts)) {
            CrestException.throwException("不存在的字库文件");
        }

        try {
            response.setContentType("application/x-download");
            response.setHeader("Content-Disposition", "attachment;filename=" + coreFonts.get(0).getFileTransName());
            File fontFile = storageService.resolve(path, coreFonts.get(0).getFileTransName());
            try (ServletOutputStream out = response.getOutputStream();
                 InputStream stream = storageService.newInputStream(fontFile)) {
                byte buff[] = new byte[1024];
                int length;
                while ((length = stream.read(buff)) > 0) {
                    out.write(buff, 0, length);
                }
                out.flush();
            }
        } catch (IOException e) {
            CrestException.throwException(e.getMessage());
        }
    }

    /**
     * 查询当前默认字体
     */
    public List<FontDto> defaultFont() {
        QueryWrapper<CoreFont> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("is_default", 1);
        List<CoreFont> coreFonts = coreFontMapper.selectList(queryWrapper);
        List<FontDto> fontDtos = new ArrayList<>();
        for (CoreFont coreFont : coreFonts) {
            FontDto dto = new FontDto();
            BeanUtils.copyBean(dto, coreFont);
            fontDtos.add(dto);
        }
        return fontDtos;
    }

    /**
     * 校验并保存上传的字体文件
     */
    @SuppressWarnings("java/path-injection")
    private FontDto saveFile(MultipartFile file, String fileNameUUID) throws CrestException {
        FontDto fontDto = new FontDto();
        try {
            String filename = file.getOriginalFilename();
            if (StringUtils.isEmpty(filename) || !filename.toLowerCase().endsWith(".ttf")) {
                CrestException.throwException("非法格式的文件！");
            }
            FileUtils.validateUploadFilename(filename);
            String suffix = filename.substring(filename.lastIndexOf(".") + 1);
            File f = storageService.resolve(path, fileNameUUID + "." + suffix);
            try (OutputStream fileOutputStream = storageService.newOutputStream(f)) {
                fileOutputStream.write(file.getBytes());
                fileOutputStream.flush();
            }
            fontDto.setFileTransName(fileNameUUID + "." + suffix);

            long length = file.getSize();
            String unit = "MB";
            Double size = 0.0;
            if ((double) length / 1024 / 1024 > 1) {
                if ((double) length / 1024 / 1024 / 1024 > 1) {
                    unit = "GB";
                    size = Double.valueOf(String.format("%.2f", (double) length / 1024 / 1024 / 1024));
                } else {
                    size = Double.valueOf(String.format("%.2f", (double) length / 1024 / 1024));
                }
            } else {
                unit = "KB";
                size = Double.valueOf(String.format("%.2f", (double) length / 1024));
            }
            Font font = Font.createFont(Font.TRUETYPE_FONT, f);
            fontDto.setSize(size);
            fontDto.setSizeType(unit);
            fontDto.setName(font.getFontName());
        } catch (Exception e) {
            CrestException.throwException(e);
        }
        return fontDto;
    }

}
