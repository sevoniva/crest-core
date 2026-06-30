package io.crest.api.msgCenter;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;

@Tag(name = "消息中心")
// 定义模块接口契约和数据传输结构
public interface MsgCenterApi {

    @PostMapping("/count")
    long count();

}
