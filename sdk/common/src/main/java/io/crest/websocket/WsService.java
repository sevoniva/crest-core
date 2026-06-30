package io.crest.websocket;



// 定义实时消息通道的配置入口
public interface WsService {

    void releaseMessage(WsMessage wsMessage);


}
