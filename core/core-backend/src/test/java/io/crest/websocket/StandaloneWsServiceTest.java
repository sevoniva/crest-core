package io.crest.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.crest.websocket.service.impl.StandaloneWsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 验证 WebSocket Redis 广播在多 API Pod 下的发布、兜底和跨实例投递行为。
class StandaloneWsServiceTest {

    @Test
    void releaseMessageShouldPublishRedisBroadcastAndSendLocalMessage() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StandaloneWsService service = service(messagingTemplate, redisTemplate);

        service.releaseMessage(new WsMessage<>(1L, "/task-export-topic", "payload"));

        verify(redisTemplate).convertAndSend(eq("{crest-core}:test:pubsub:websocket"), org.mockito.ArgumentMatchers.contains("/task-export-topic"));
        verify(messagingTemplate).convertAndSendToUser("1", "/task-export-topic", "payload");
    }

    @Test
    void releaseMessageShouldKeepLocalDeliveryWhenRedisPublishFails() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        doThrow(new IllegalStateException("redis down")).when(redisTemplate)
                .convertAndSend(eq("{crest-core}:test:pubsub:websocket"), org.mockito.ArgumentMatchers.anyString());
        StandaloneWsService service = service(messagingTemplate, redisTemplate);

        service.releaseMessage(new WsMessage<>(2L, "/task-export-topic", "payload"));

        verify(messagingTemplate).convertAndSendToUser("2", "/task-export-topic", "payload");
    }

    @Test
    void redisBroadcastMessageFromAnotherInstanceShouldSendLocalMessage() throws Exception {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        StandaloneWsService service = service(messagingTemplate, mock(StringRedisTemplate.class));
        ObjectMapper objectMapper = new ObjectMapper();
        String payload = objectMapper.writeValueAsString(new StandaloneWsService.WsBroadcastEnvelope(
                "other-instance",
                new WsMessage<>(3L, "/task-export-topic", "payload")
        ));
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(payload.getBytes(StandardCharsets.UTF_8));

        ReflectionTestUtils.invokeMethod(service, "handleRedisMessage", message, null);

        verify(messagingTemplate).convertAndSendToUser("3", "/task-export-topic", "payload");
    }

    @SuppressWarnings("unchecked")
    private StandaloneWsService service(SimpMessagingTemplate messagingTemplate, StringRedisTemplate redisTemplate) {
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(redisTemplate);
        StandaloneWsService service = new StandaloneWsService();
        ReflectionTestUtils.setField(service, "messagingTemplate", messagingTemplate);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(service, "environment", new MockEnvironment()
                .withProperty("crest.websocket.broadcast.enabled", "true")
                .withProperty("crest.production-mode", "true")
                .withProperty("CREST_RUNTIME_ROLE", "api"));
        ReflectionTestUtils.setField(service, "redisTemplateProvider", provider);
        ReflectionTestUtils.setField(service, "broadcastChannel", "{crest-core}:test:pubsub:websocket");
        return service;
    }
}
