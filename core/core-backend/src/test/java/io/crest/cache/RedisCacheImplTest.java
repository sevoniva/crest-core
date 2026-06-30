package io.crest.cache;

import io.crest.auth.vo.TokenVO;
import io.crest.cache.impl.RedisCacheImpl;
import io.crest.constant.CacheConstant;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisCacheImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldStoreReadAndRemoveSharedSsoTicketCache() {
        RedisCacheImpl cacheService = new RedisCacheImpl();
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> operations = mock(ValueOperations.class);
        ReflectionTestUtils.setField(cacheService, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(cacheService, "redisKeyPrefix", "{crest-core}:test:cache:");
        when(redisTemplate.opsForValue()).thenReturn(operations);
        when(operations.get("{crest-core}:test:cache:crest_sso_ticket::ticket-1")).thenReturn("token-value");
        when(redisTemplate.hasKey("{crest-core}:test:cache:crest_sso_ticket::ticket-1")).thenReturn(true);

        cacheService.put(CacheConstant.UserCacheConstant.SSO_TICKET_CACHE,
                "ticket-1", "token-value", 1L, TimeUnit.MINUTES);

        assertThat(cacheService.get(CacheConstant.UserCacheConstant.SSO_TICKET_CACHE, "ticket-1"))
                .isEqualTo("token-value");
        assertThat(cacheService.keyExist(CacheConstant.UserCacheConstant.SSO_TICKET_CACHE, "ticket-1"))
                .isTrue();
        cacheService.keyRemove(CacheConstant.UserCacheConstant.SSO_TICKET_CACHE, "ticket-1");

        // SSO 票据必须落到稳定 Redis key，保证任意 API Pod 都能消费。
        verify(operations).set("{crest-core}:test:cache:crest_sso_ticket::ticket-1", "token-value", 1L, TimeUnit.MINUTES);
        verify(redisTemplate).delete("{crest-core}:test:cache:crest_sso_ticket::ticket-1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldStoreTokenVoInSharedSsoTicketCache() {
        RedisCacheImpl cacheService = new RedisCacheImpl();
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> operations = mock(ValueOperations.class);
        TokenVO token = new TokenVO("signed-token", 60000L);
        ReflectionTestUtils.setField(cacheService, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(cacheService, "redisKeyPrefix", "{crest-core}:test:cache:");
        when(redisTemplate.opsForValue()).thenReturn(operations);
        when(operations.get("{crest-core}:test:cache:crest_sso_ticket::ticket-2")).thenReturn(token);

        cacheService.put(CacheConstant.UserCacheConstant.SSO_TICKET_CACHE,
                "ticket-2", token, 1L, TimeUnit.MINUTES);

        assertThat(cacheService.get(CacheConstant.UserCacheConstant.SSO_TICKET_CACHE, "ticket-2"))
                .isSameAs(token);
        // SSO 票据缓存承载 TokenVO 对象，供任意 API Pod 完成一次性兑换。
        verify(operations).set("{crest-core}:test:cache:crest_sso_ticket::ticket-2", token, 1L, TimeUnit.MINUTES);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldStoreStateObjectInSharedSsoStateCache() throws Exception {
        RedisCacheImpl cacheService = new RedisCacheImpl();
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ValueOperations<String, Object> operations = mock(ValueOperations.class);
        Object state = newSsoState("/portal", "nonce-1");
        ReflectionTestUtils.setField(cacheService, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(cacheService, "redisKeyPrefix", "{crest-core}:test:cache:");
        when(redisTemplate.opsForValue()).thenReturn(operations);
        when(operations.get("{crest-core}:test:cache:crest_sso_state::state-1")).thenReturn(state);

        cacheService.put(CacheConstant.UserCacheConstant.SSO_STATE_CACHE,
                "state-1", state, 10L, TimeUnit.MINUTES);

        assertThat(cacheService.get(CacheConstant.UserCacheConstant.SSO_STATE_CACHE, "state-1"))
                .isSameAs(state);
        // SSO state 必须走共享缓存，避免回调请求落到其他 API Pod 后丢会话。
        verify(operations).set("{crest-core}:test:cache:crest_sso_state::state-1", state, 10L, TimeUnit.MINUTES);
    }

    private Object newSsoState(String redirect, String nonce) throws Exception {
        Class<?> stateClass = Class.forName("io.crest.system.manage.SsoManage$SsoState");
        Constructor<?> constructor = stateClass.getDeclaredConstructor(String.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(redirect, nonce);
    }
}
