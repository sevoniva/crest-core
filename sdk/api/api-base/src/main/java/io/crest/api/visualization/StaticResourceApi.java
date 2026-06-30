package io.crest.api.visualization;

import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import io.crest.api.visualization.request.StaticResourceRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
@Tag(name = "可视化管理:静态文件")
@ApiSupport(order = 998)
// 定义模块接口契约和数据传输结构
public interface StaticResourceApi {
    @PostMapping("upload/{fileId}")
    @Operation(summary = "上传静态文件")
    void upload(@PathVariable("fileId") String fileId, @RequestPart("file") MultipartFile file);

    @PostMapping("resource-base64")
    @Operation(summary = "查找静态文件并转为Base64")
    Map<String,String> resourceBase64(@RequestBody StaticResourceRequest resourceRequest);

}
