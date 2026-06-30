package io.crest.datasource.type;


import lombok.Data;
import org.springframework.stereotype.Component;

@Data
// 定义数据源连接参数和元数据匹配规则
public class Es {
    private String url;
    private String username;
    private String password;
    private String version;
    private String uri;

}
