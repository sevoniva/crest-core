package io.crest.api.dingtalk.api;

import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import io.crest.api.dingtalk.dto.*;
import io.crest.api.dingtalk.vo.DingtalkInfoVO;
import io.crest.api.lark.vo.LarkGroupVO;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;

@Tag(name = "钉钉设置")
@ApiSupport(order = 899)
// 定义模块接口契约和数据传输结构
public interface DingtalkApi {

    @Operation(summary = "查询钉钉信息")
    @GetMapping("/info")
    DingtalkInfoVO info();

    @Operation(summary = "查询钉钉二维码信息")
    @GetMapping("/qrinfo")
    DingtalkInfoVO qrinfo();

    @Operation(summary = "保存")
    @PostMapping
    void save(@RequestBody DingtalkSettingCreator creator);

    @Operation(summary = "钉钉token", hidden = true)
    @PostMapping("/token")
    String dingtalkToken(@RequestBody DingtalkTokenRequest request);

    @Operation(summary = "切换开启状态")
    @PostMapping("/enabled")
    void switchEnable(@RequestBody DingtalkEnableEditor editor);

    @Operation(summary = "验证可用性")
    @PostMapping("/validate")
    void validate(@RequestBody DingtalkSettingCreator creator);

    @Operation(summary = "钉钉绑定", hidden = true)
    @PostMapping("/bind")
    void bind(@RequestBody DingtalkTokenRequest request);

    @Operation(summary = "获取群组", hidden = true)
    @GetMapping("/groups")
    LarkGroupVO getGroup();

    @Hidden
    @PostMapping("/signature-info")
    DingtalkSignatureInfo getSignatureInfo(@RequestBody SignatureRequest request);

    @Hidden
    @PostMapping("/chat-check")
    void checkChat(@RequestBody DingtalkChatCheckRequest request);
}
