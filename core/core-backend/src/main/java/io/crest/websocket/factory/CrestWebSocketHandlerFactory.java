package io.crest.websocket.factory;

import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

// 定义实时消息通道的配置入口
public class CrestWebSocketHandlerFactory implements WebSocketHandlerDecoratorFactory {


    @Override
    public WebSocketHandler decorate(WebSocketHandler webSocketHandler) {
        return new CrestWebSocketHandlerDecorator(webSocketHandler);
    }
}
