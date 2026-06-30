package io.crest.api.visualization.request;


import lombok.Data;
import java.util.List;

@Data
// 定义接口请求或返回数据的传输结构
public class StaticResourceRequest {
    private List<String> resourcePathList;

}
