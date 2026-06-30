package io.crest.api.font.api;

import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import io.crest.api.font.dto.FontDto;
import io.crest.exception.CrestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


@Tag(name = "数据集管理:数据")
@ApiSupport(order = 972)
/**
 * 字体管理接口，负责字体列表、上传下载和默认字体配置
 */
public interface FontApi {

    /**
     * 查询当前系统可用的字体列表
     */
    @Operation(summary = "预览数据")
    @GetMapping("fonts")
    List<FontDto> list() throws Exception;

    /**
     * 新增字体配置记录
     */
    @Operation(summary = "创建")
    @PostMapping
    public FontDto create(@RequestBody FontDto fontDto);

    /**
     * 更新已有字体配置记录
     */
    @Operation(summary = "编辑")
    @PutMapping
    public FontDto edit(@RequestBody FontDto fontDto);

    /**
     * 根据字体编号删除字体配置
     */
    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id);

    /**
     * 修改默认字体设置
     */
    @Operation(summary = "变更默认设置")
    @PostMapping("/default/")
    public void changeDefault(@RequestBody FontDto fontDto);

    /**
     * 上传字体文件并生成字体配置
     */
    @PostMapping("/files/upload")
    FontDto upload(@RequestParam("file") MultipartFile file) throws CrestException;

    /**
     * 下载指定字体文件
     */
    @GetMapping("/download/{file}")
    void download(@PathVariable("file") String file, HttpServletResponse response) throws CrestException;

    /**
     * 查询系统默认字体列表
     */
    @GetMapping("/default")
    List<FontDto> defaultFont() throws CrestException;
}
