package io.crest.api.wecom.api;

import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import io.crest.api.wecom.dto.WecomCreator;
import io.crest.api.wecom.dto.WecomEnableEditor;
import io.crest.api.wecom.dto.WecomTokenRequest;
import io.crest.api.wecom.vo.WecomInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;

@Tag(name = "企微设置")
@ApiSupport(order = 899)
// 定义模块接口契约和数据传输结构
public interface WecomApi {

    @Operation(summary = "查询企微信息")
    @GetMapping("/info")
    WecomInfoVO info();

    @Operation(summary = "查询企微二维码信息")
    @GetMapping("/qrinfo")
    WecomInfoVO qrinfo();

    @Operation(summary = "保存")
    @PostMapping
    void save(@RequestBody WecomCreator creator);

    @Operation(summary = "企微token", hidden = true)
    @PostMapping("/token")
    String wecomToken(@RequestBody WecomTokenRequest request);

    @Operation(summary = "切换开启状态")
    @PostMapping("/enabled")
    void switchEnable(@RequestBody WecomEnableEditor editor);

    @Operation(summary = "验证可用性")
    @PostMapping("/validate")
    void validate(@RequestBody WecomCreator creator);

    @Operation(summary = "企微绑定用户", hidden = true)
    @PostMapping("/bind")
    void bind(@RequestBody WecomTokenRequest request);
}
