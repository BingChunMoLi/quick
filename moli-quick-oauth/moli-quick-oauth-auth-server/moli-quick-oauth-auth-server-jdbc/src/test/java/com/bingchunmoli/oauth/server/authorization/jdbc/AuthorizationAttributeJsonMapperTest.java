package com.bingchunmoli.oauth.server.authorization.jdbc;

import com.bingchunmoli.oauth.server.authorization.SecurityAuthorizationAttributeCodec;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import static org.assertj.core.api.Assertions.*;

class AuthorizationAttributeJsonMapperTest {
    @Test
    void previouslyStoredNativeMapsRemainReadable() {
        var codec=new SecurityAuthorizationAttributeCodec();
        var mapper=AuthorizationAttributeJsonMapper.create(codec);
        Map<String,Object> attributes=new LinkedHashMap<>(Map.of("state","legacy-state","issued",Instant.parse("2026-10-19T00:00:00Z")));
        Map<String,Object> recovered=mapper.readValue(codec.encode(attributes),new TypeReference<Map<String,Object>>() { });
        assertThat(recovered).isEqualTo(attributes);
    }

    @Test
    void unknownEnvelopeVersionsAndNonTextPayloadsAreRejected() {
        var mapper=AuthorizationAttributeJsonMapper.create(new SecurityAuthorizationAttributeCodec());
        for(String document:new String[]{"{\"quickAttributeCodecVersion\":2,\"payload\":\"hidden-value\"}",
                "{\"quickAttributeCodecVersion\":1,\"payload\":{\"secret\":\"hidden-value\"}}"}) {
            assertThatThrownBy(()->mapper.readValue(document,new TypeReference<Map<String,Object>>() { }))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Invalid authorization attribute codec envelope");
        }
    }
    @Test void oversizedEnvelopeVersionCannotWrapToSupportedVersion() {
        var codec=new SecurityAuthorizationAttributeCodec();
        var mapper=AuthorizationAttributeJsonMapper.create(codec);
        String payload=tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(codec.encode(Map.of("state","test-state")));
        for(String version:new String[]{"4294967297","18446744073709551617"}) {
            assertThatThrownBy(()->mapper.readValue("{\"quickAttributeCodecVersion\":"+version+",\"payload\":"+payload+"}",new TypeReference<Map<String,Object>>() { }))
                    .isInstanceOf(IllegalStateException.class).hasMessage("Invalid authorization attribute codec envelope");
        }
    }
}
