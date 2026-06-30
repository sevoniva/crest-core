package io.crest.api.sync.task.dto;

import io.crest.model.KeywordRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
// 定义接口请求或返回数据的传输结构
public class TaskGridRequest extends KeywordRequest implements Serializable {
    private List<String> logStatus;
    private List<String> status;
    private List<String> lastExecuteTime;
    private List<String> nextExecuteTime;
}
