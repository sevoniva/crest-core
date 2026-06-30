package io.crest.websocket.entity;

import java.security.Principal;

// 定义实时消息通道的配置入口
public class CrestPrincipal implements Principal {

    public CrestPrincipal(String name) {
        this.name = name;
    }

    private String name;

    @Override
    public String getName() {
        return name;
    }
}
