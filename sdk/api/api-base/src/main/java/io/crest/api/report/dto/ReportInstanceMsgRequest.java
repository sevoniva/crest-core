package io.crest.api.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Schema(description = "日志错误信息请求")
@Data
// 定义接口请求或返回数据的传输结构
public class ReportInstanceMsgRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 7192222037770564561L;
    @Schema(description = "任务ID")
    private Long taskId;
    @Schema(description = "任务实例ID")
    private Long instanceId;
}
