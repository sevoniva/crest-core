package io.crest.api.webhook;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import io.crest.api.webhook.request.WebhookSwitchRequest;
import io.crest.api.webhook.vo.WebhookGridVO;
import io.crest.api.webhook.vo.WebhookOption;
import io.crest.model.KeywordRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Webhook 管理")
@ApiSupport(order = 888, author = "Crest")
// 定义模块接口契约和数据传输结构
public interface WebhookApi {

    @Operation(summary = "查询列表")
    @Parameters({
            @Parameter(name = "goPage", description = "目标页码", required = true, in = ParameterIn.PATH),
            @Parameter(name = "pageSize", description = "每页容量", required = true, in = ParameterIn.PATH),
            @Parameter(name = "request", description = "过滤条件", required = true)
    })
    @PostMapping("/page/{goPage}/{pageSize}")
    IPage<WebhookGridVO> pager(@PathVariable("goPage") int goPage, @PathVariable("pageSize") int pageSize, @RequestBody KeywordRequest request);

    @Operation(summary = "保存")
    @PostMapping("/record")
    void save(@RequestBody WebhookGridVO creator);

    @Operation(summary = "切换SSL")
    @PostMapping("/ssl/enabled")
    void switchSsl(@RequestBody WebhookSwitchRequest request);

    @Operation(summary = "删除")
    @DeleteMapping
    void delete(@RequestBody List<Long> ids);

    @Operation(summary = "查询选项")
    @GetMapping("/options")
    List<WebhookOption> options();
}
