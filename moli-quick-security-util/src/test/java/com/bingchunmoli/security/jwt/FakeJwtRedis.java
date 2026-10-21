package com.bingchunmoli.security.jwt;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** In-process Redis primitives with independent serialized session snapshots and atomic CAS. */
final class FakeJwtRedis {
    final RedisTemplate<String, Object> template;
    final Map<String, Object> values;
    final Map<String, Set<Object>> sets;
    final Map<String, Long> ttls;
    Runnable beforeCompareAndSet = () -> { };
    Runnable beforeIndexRemoval = () -> { };
    Runnable afterSnapshotRead = () -> { };
    private final Map<String, byte[]> bytes = new ConcurrentHashMap<>();
    private final RedisSerializer<Object> serializer = new SnapshotSerializer();

    @SuppressWarnings({"unchecked", "rawtypes"})
    FakeJwtRedis(Map<String, Object> values, Map<String, Set<Object>> sets, Map<String, Long> ttls) {
        this.values = values;
        this.sets = sets;
        this.ttls = ttls;
        template = mock(RedisTemplate.class);
        ValueOperations valueOperations = mock(ValueOperations.class);
        SetOperations setOperations = mock(SetOperations.class);
        when(template.opsForValue()).thenReturn(valueOperations);
        when(template.opsForSet()).thenReturn(setOperations);
        doReturn(serializer).when(template).getValueSerializer();
        doReturn(RedisSerializer.string()).when(template).getKeySerializer();
        when(valueOperations.get(any())).thenAnswer(call -> copy(values.get(call.getArgument(0))));
        doAnswer(call -> {
            String key = call.getArgument(0);
            Object value = call.getArgument(1);
            TimeUnit unit = call.getArgument(3);
            synchronized (values) {
                values.put(key, copy(value));
                bytes.put(key, serializer.serialize(value));
                ttls.put(key, unit.toMillis(call.getArgument(2)));
            }
            return null;
        }).when(valueOperations).set(any(), any(), anyLong(), any(TimeUnit.class));
        when(template.expire(any(), anyLong(), any(TimeUnit.class))).thenAnswer(call -> {
            TimeUnit unit = call.getArgument(2);
            ttls.put(call.getArgument(0), unit.toMillis(call.getArgument(1)));
            return true;
        });
        when(setOperations.members(any())).thenAnswer(call -> {
            Set<Object> members = sets.get(call.getArgument(0));
            return members == null ? null : Set.copyOf(members);
        });
        when(setOperations.remove(any(), any(Object[].class))).thenAnswer(call -> {
            Runnable hook = beforeIndexRemoval;
            beforeIndexRemoval = () -> { };
            hook.run();
            String key = call.getArgument(0);
            Object[] members = (Object[]) call.getRawArguments()[1];
            long removed = 0;
            synchronized (sets) {
                Set<Object> existing = sets.get(key);
                if (existing != null) {
                    for (Object member : members) {
                        if (existing.remove(member)) {
                            removed++;
                        }
                    }
                    if (existing.isEmpty()) {
                        sets.remove(key);
                        ttls.remove(key);
                    }
                }
            }
            return removed;
        });
        RedisConnection connection = mock(RedisConnection.class);
        RedisStringCommands strings = mock(RedisStringCommands.class);
        when(connection.stringCommands()).thenReturn(strings);
        when(strings.get(any(byte[].class))).thenAnswer(call -> {
            String key = new String((byte[]) call.getArgument(0), StandardCharsets.UTF_8);
            byte[] snapshot;
            synchronized (values) {
                snapshot = values.containsKey(key) ? bytes.computeIfAbsent(key, ignored -> serializer.serialize(values.get(key))) : null;
            }
            Runnable hook = afterSnapshotRead;
            afterSnapshotRead = () -> { };
            hook.run();
            return snapshot;
        });
        when(template.execute(any(RedisCallback.class))).thenAnswer(call ->
                ((RedisCallback<?>) call.getArgument(0)).doInRedis(connection));
        when(template.execute(any(RedisScript.class), any(RedisSerializer.class), isNull(), anyList(),
                any(byte[].class), any(byte[].class))).thenAnswer(call -> {
            RedisScript<?> script = call.getArgument(0);
            String key = ((List<String>) call.getArgument(3)).get(0);
            byte[] first = call.getArgument(4);
            byte[] second = call.getArgument(5);
            if (script.getScriptAsString().contains("sadd")) {
                Object member = serializer.deserialize(first);
                long ttl = Long.parseLong(new String(second, StandardCharsets.UTF_8));
                synchronized (sets) {
                    boolean existed = sets.containsKey(key);
                    sets.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet()).add(member);
                    if (!existed || ttls.containsKey(key) && ttls.get(key) < ttl) {
                        ttls.put(key, ttl);
                    }
                }
                return 1L;
            }
            Runnable hook = beforeCompareAndSet;
            beforeCompareAndSet = () -> { };
            hook.run();
            synchronized (values) {
                if (!values.containsKey(key) || !Arrays.equals(bytes.get(key), first)) {
                    return 0L;
                }
                values.put(key, serializer.deserialize(second));
                bytes.put(key, second);
                return 1L;
            }
        });
    }

    private static Object copy(Object value) {
        if (!(value instanceof JwtSession session)) {
            return value;
        }
        JwtSession copy = new JwtSession();
        copy.setSessionId(session.getSessionId());
        copy.setTokenId(session.getTokenId());
        copy.setUser(session.getUser());
        copy.setCreatedAt(session.getCreatedAt());
        copy.setLastAccessAt(session.getLastAccessAt());
        copy.setExpiresAt(session.getExpiresAt());
        copy.setActiveExpiresAt(session.getActiveExpiresAt());
        copy.setExpired(session.isExpired());
        return copy;
    }

    private static final class SnapshotSerializer implements RedisSerializer<Object> {
        private final AtomicLong sequence = new AtomicLong();
        private final Map<String, Object> snapshots = new ConcurrentHashMap<>();

        @Override
        public byte[] serialize(Object value) {
            String encoded;
            if (value instanceof String text) {
                encoded = "S:" + text;
            } else if (value instanceof Boolean flag) {
                encoded = "B:" + flag;
            } else {
                encoded = "V:" + sequence.incrementAndGet();
                snapshots.put(encoded, copy(value));
            }
            return encoded.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public Object deserialize(byte[] bytes) {
            String encoded = new String(bytes, StandardCharsets.UTF_8);
            if (encoded.startsWith("S:")) {
                return encoded.substring(2);
            }
            if (encoded.startsWith("B:")) {
                return Boolean.valueOf(encoded.substring(2));
            }
            return copy(snapshots.get(encoded));
        }
    }
}
