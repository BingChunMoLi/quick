package com.bingchunmoli.autoconfigure.redis.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedisUtilTest {

    private RedisTemplate redisTemplate;
    private RedisUtil redisUtil;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        redisUtil = new RedisUtil(redisTemplate);
    }

    @Test
    void setAndGetObjectShouldDelegateToValueOperations() {
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("name")).thenReturn("moli");

        redisUtil.setObject("name", "moli", 5, TimeUnit.SECONDS);
        String value = redisUtil.getObject("name");

        assertThat(value).isEqualTo("moli");
        verify(valueOperations).set("name", "moli", 5, TimeUnit.SECONDS);
    }

    @Test
    void setListShouldReturnZeroWhenRedisReturnsNull() {
        ListOperations<String, String> listOperations = mock(ListOperations.class);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.rightPushAll("items", List.of("a", "b"))).thenReturn(null);

        long count = redisUtil.setList("items", List.of("a", "b"));

        assertThat(count).isZero();
    }

    @Test
    void mapOperationsShouldDelegateToHashOperations() {
        HashOperations<String, String, Integer> hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get("map", "age")).thenReturn(18);

        redisUtil.setMapValue("map", "age", 18);
        Integer age = redisUtil.getMapValue("map", "age");

        assertThat(age).isEqualTo(18);
        verify(hashOperations).put("map", "age", 18);
    }

    @Test
    void getSetShouldReturnMembers() {
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("roles")).thenReturn(Set.of("admin"));

        Set<String> roles = redisUtil.getSet("roles");

        assertThat(roles).containsExactly("admin");
    }

    @Test
    void setMapShouldIgnoreNullMap() {
        redisUtil.setMap("map", null);

        verifyNoInteractions(redisTemplate);
    }
}
