package io.crest.websocket.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.crest.runtime.CrestRuntimeRole;
import io.crest.utils.LogUtil;
import io.crest.websocket.WsMessage;
import io.crest.websocket.WsService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
// 统一处理 WebSocket 本地投递和 Redis 跨 Pod 广播。
public class StandaloneWsService implements WsService {

    @Resource
    private SimpMessagingTemplate messagingTemplate;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private Environment environment;
    @Autowired
    private ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;

    @Value("${crest.websocket.broadcast.channel:crest:core:pubsub:websocket}")
    private String broadcastChannel;

    private final String instanceId = buildInstanceId();
    private RedisMessageListenerContainer listenerContainer;

    @PostConstruct
    public void startRedisBroadcastListener() {
        if (!broadcastEnabled() || !CrestRuntimeRole.from(environment).servesApi()) {
            return;
        }
        if (redisConnectionFactory == null) {
            if (productionMode()) {
                LogUtil.error("Redis WebSocket broadcast listener skipped because RedisConnectionFactory is not available");
            }
            return;
        }
        try {
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(redisConnectionFactory);
            container.addMessageListener(this::handleRedisMessage, new ChannelTopic(broadcastChannel));
            container.afterPropertiesSet();
            container.start();
            listenerContainer = container;
        } catch (Exception e) {
            LogUtil.error("Redis WebSocket broadcast listener failed to start", e);
        }
    }

    @PreDestroy
    public void stopRedisBroadcastListener() {
        if (listenerContainer == null) {
            return;
        }
        try {
            listenerContainer.stop();
            listenerContainer.destroy();
        } catch (Exception e) {
            LogUtil.warn("Redis WebSocket broadcast listener stop failed: " + e.getMessage());
        }
    }

    @Override
    public void releaseMessage(WsMessage wsMessage) {
        if (!validMessage(wsMessage)) {
            return;
        }
        if (!broadcastEnabled()) {
            sendLocal(wsMessage);
            return;
        }
        if (publishRedisMessage(wsMessage)) {
            sendLocal(wsMessage);
            return;
        }
        // Redis 不可用时保留本机兜底投递，生产 readiness 会负责把实例摘出流量。
        sendLocal(wsMessage);
    }

    private boolean publishRedisMessage(WsMessage wsMessage) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            if (productionMode()) {
                LogUtil.error("Redis WebSocket broadcast skipped because StringRedisTemplate is not available");
            }
            return false;
        }
        try {
            WsBroadcastEnvelope envelope = new WsBroadcastEnvelope(instanceId, wsMessage);
            redisTemplate.convertAndSend(broadcastChannel, objectMapper.writeValueAsString(envelope));
            return true;
        } catch (Exception e) {
            LogUtil.error("Redis WebSocket broadcast publish failed", e);
            return false;
        }
    }

    private void handleRedisMessage(Message message, byte[] pattern) {
        try {
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            WsBroadcastEnvelope envelope = objectMapper.readValue(payload, WsBroadcastEnvelope.class);
            if (envelope == null || Strings.CS.equals(instanceId, envelope.getOriginId())) {
                return;
            }
            sendLocal(envelope.getMessage());
        } catch (Exception e) {
            LogUtil.warn("Redis WebSocket broadcast message ignored: " + e.getMessage());
        }
    }

    // 本地投递只依赖当前 Pod 的 STOMP 会话，用户不在本机时 Spring broker 会自然丢弃。
    private void sendLocal(WsMessage wsMessage) {
        if (!validMessage(wsMessage)) {
            return;
        }
        messagingTemplate.convertAndSendToUser(String.valueOf(wsMessage.getUserId()), wsMessage.getTopic(), wsMessage.getData());
    }

    private boolean validMessage(WsMessage wsMessage) {
        return !ObjectUtils.isEmpty(wsMessage)
                && !ObjectUtils.isEmpty(wsMessage.getUserId())
                && !ObjectUtils.isEmpty(wsMessage.getTopic());
    }

    private boolean broadcastEnabled() {
        String explicit = StringUtils.defaultIfBlank(environment.getProperty("crest.websocket.broadcast.enabled"),
                environment.getProperty("CREST_WEBSOCKET_BROADCAST_ENABLED"));
        if (StringUtils.isNotBlank(explicit)) {
            return Boolean.parseBoolean(explicit.trim());
        }
        return productionMode();
    }

    private boolean productionMode() {
        return environment.getProperty("crest.production-mode", Boolean.class,
                environment.getProperty("CREST_PRODUCTION_MODE", Boolean.class, false));
    }

    private String buildInstanceId() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID();
        } catch (Exception e) {
            return "ws-" + UUID.randomUUID();
        }
    }

    public static class WsBroadcastEnvelope {
        private String originId;
        private WsMessage<Object> message;

        public WsBroadcastEnvelope() {
        }

        public WsBroadcastEnvelope(String originId, WsMessage<?> message) {
            this.originId = originId;
            this.message = new WsMessage<>(message.getUserId(), message.getTopic(), message.getData());
        }

        public String getOriginId() {
            return originId;
        }

        public void setOriginId(String originId) {
            this.originId = originId;
        }

        public WsMessage<Object> getMessage() {
            return message;
        }

        public void setMessage(WsMessage<Object> message) {
            this.message = message;
        }
    }
}
