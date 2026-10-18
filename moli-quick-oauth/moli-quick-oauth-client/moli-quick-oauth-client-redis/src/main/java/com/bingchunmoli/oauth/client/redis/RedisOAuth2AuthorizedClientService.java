package com.bingchunmoli.oauth.client.redis;

import com.bingchunmoli.oauth.client.*;
import java.time.*;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

public final class RedisOAuth2AuthorizedClientService implements OAuth2AuthorizedClientService {
    static String tokenKey(AuthorizedClientRef ref) { return "moli:oauth2:{" + TokenSnapshotCodec.key(ref) + "}:client"; }
    // Lock ownership and token mutation are checked atomically on Redis.
    private static final DefaultRedisScript<Long> SAVE = new DefaultRedisScript<>(
            "if ARGV[1] ~= '' and redis.call('get',KEYS[2]) ~= ARGV[1] then return 0 end " +
            "if tonumber(ARGV[3]) > 0 then redis.call('psetex',KEYS[1],ARGV[3],ARGV[2]) else redis.call('set',KEYS[1],ARGV[2]) end return 1", Long.class);
    private static final DefaultRedisScript<Long> REMOVE = new DefaultRedisScript<>(
            "if ARGV[1] ~= '' and redis.call('get',KEYS[2]) ~= ARGV[1] then return 0 end redis.call('del',KEYS[1]) return 1", Long.class);
    private final StringRedisTemplate redis;
    private final ClientRegistrationRepository registrations;
    private final RedisRefreshCoordinator coordinator;
    private final String prefix;
    public RedisOAuth2AuthorizedClientService(StringRedisTemplate redis, ClientRegistrationRepository registrations,
                                               RedisRefreshCoordinator coordinator) {
        this(redis, registrations, coordinator, "moli:oauth2");
    }
    public RedisOAuth2AuthorizedClientService(StringRedisTemplate redis, ClientRegistrationRepository registrations,
                                               RedisRefreshCoordinator coordinator, String prefix) {
        this.redis = redis; this.registrations = registrations; this.coordinator = coordinator; this.prefix = prefix;
    }
    private String key(AuthorizedClientRef ref) { return prefix + ":{" + TokenSnapshotCodec.key(ref) + "}:client"; }
    @Override @SuppressWarnings("unchecked")
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String id, String principal) {
        String data = redis.opsForValue().get(key(new AuthorizedClientRef(id, principal)));
        if (data == null) return null;
        OAuth2AuthorizedClient client = TokenSnapshotCodec.decode(data, registrations);
        if (!id.equals(client.getClientRegistration().getRegistrationId()) || !principal.equals(client.getPrincipalName())) {
            throw new IllegalStateException("OAuth2 authorization identity mismatch");
        }
        return (T) client;
    }
    @Override public void saveAuthorizedClient(OAuth2AuthorizedClient client, Authentication principal) {
        var ref = new AuthorizedClientRef(client.getClientRegistration().getRegistrationId(), client.getPrincipalName());
        String key = key(ref);
        Instant expiration = TokenSnapshotCodec.effectiveExpiration(client);
        long ttl = expiration == null ? 0 : Math.max(1, Duration.between(Instant.now(), expiration).toMillis());
        var lease = coordinator == null ? null : coordinator.ownership();
        if (lease != null && lease.lost().get()) throw new IllegalStateException("OAuth2 refresh ownership lost");
        Long result = redis.execute(SAVE, List.of(key, lease == null ? key : lease.key()),
                lease == null ? "" : lease.owner(), TokenSnapshotCodec.encode(client), String.valueOf(ttl));
        if (!Long.valueOf(1).equals(result)) throw new IllegalStateException("OAuth2 refresh ownership lost");
    }
    @Override public void removeAuthorizedClient(String id, String principal) {
        String key = key(new AuthorizedClientRef(id, principal));
        var lease = coordinator == null ? null : coordinator.ownership();
        Long result = redis.execute(REMOVE, List.of(key, lease == null ? key : lease.key()), lease == null ? "" : lease.owner());
        if (!Long.valueOf(1).equals(result)) throw new IllegalStateException("OAuth2 refresh ownership lost");
    }
}
