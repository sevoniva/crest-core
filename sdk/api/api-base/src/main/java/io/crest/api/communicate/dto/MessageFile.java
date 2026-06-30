package io.crest.api.communicate.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
// 定义接口请求或返回数据的传输结构
public class MessageFile implements Serializable {
    @Serial
    private static final long serialVersionUID = 7140452847688399889L;

    private String fileName;

    private byte[] body;

}
