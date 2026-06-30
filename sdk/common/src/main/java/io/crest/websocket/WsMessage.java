package io.crest.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
// 定义实时消息通道的配置入口
public class WsMessage<T> implements Serializable {
    private Long userId;

    private String topic;

    private T data;


}

