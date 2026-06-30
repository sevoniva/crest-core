package io.crest.cache;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisTemplateConfigTest {

    @Test
    void shouldUseReadableKeysAndSerializableValues() {
        RedisTemplateConfig config = new RedisTemplateConfig();
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

        RedisTemplate<String, Object> template = config.redisTemplate(connectionFactory);

        // Redis 缓存键必须可读，值保留 Java 对象序列化能力。
        assertThat(template.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getHashKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getValueSerializer()).isInstanceOf(JdkSerializationRedisSerializer.class);
        assertThat(template.getHashValueSerializer()).isInstanceOf(JdkSerializationRedisSerializer.class);
    }
}
