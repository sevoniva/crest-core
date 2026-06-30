package io.crest.api.share;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.crest.api.share.request.TicketCreator;
import io.crest.api.share.request.TicketDelRequest;
import io.crest.api.share.request.TicketSwitchRequest;
import io.crest.api.share.vo.TicketVO;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;

@Tag(name = "可视化管理:分享:TICKET")
// 定义模块接口契约和数据传输结构
public interface ShareTicketApi {

    @PostMapping("/tickets")
    @Operation(summary = "保存Ticket")
    String saveTicket(@RequestBody TicketCreator creator);

    @DeleteMapping("/tickets")
    @Operation(summary = "删除Ticket")
    void deleteTicket(@RequestBody TicketDelRequest request);

    @PostMapping("/tickets/enabled")
    @Operation(summary = "切换Ticket必填状态")
    void switchRequire(@RequestBody TicketSwitchRequest request);

    @PostMapping("/page/{resourceId}/{goPage}/{pageSize}")
    @Operation(summary = "根据资源查询Ticket")
    @Parameter(name = "resourceId", description = "资源ID", required = true, in = ParameterIn.PATH)
    IPage<TicketVO> pager(@PathVariable("resourceId") Long resourceId, @PathVariable("goPage") int goPage, @PathVariable("pageSize") int pageSize);

    @GetMapping("/tickets/temporary")
    @Operation(summary = "生成临时Ticket")
    String tempTicket();

    @GetMapping("/limit")
    @Hidden
    Integer limit();
}
