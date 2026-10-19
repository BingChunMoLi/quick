package com.bingchunmoli.oauth.server.authorization.jdbc;

import com.bingchunmoli.oauth.server.authorization.AuthorizationAttributeCodec;
import java.util.Map;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/** Applies the same explicit attribute codec to native JDBC attribute and token-metadata maps. */
final class AuthorizationAttributeJsonMapper {
    private AuthorizationAttributeJsonMapper() { }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static JsonMapper create(AuthorizationAttributeCodec attributes) {
        var module = new SimpleModule("quick-oauth-authorization-attributes");
        module.addSerializer(Map.class, new ValueSerializer<Map>() {
            @Override
            public void serialize(Map value, JsonGenerator output, SerializationContext context) {
                output.writeStartObject();
                output.writeNumberProperty("quickAttributeCodecVersion", 1);
                output.writeStringProperty("payload", attributes.encode(value));
                output.writeEndObject();
            }
        });
        module.addDeserializer(Map.class, new ValueDeserializer<Map>() {
            @Override
            public Map deserialize(JsonParser input, DeserializationContext context) {
                var document=context.readTree(input);
                if(document.has("quickAttributeCodecVersion")) {
                    var version=document.path("quickAttributeCodecVersion");
                    if(!version.isIntegralNumber() || !version.canConvertToInt() || version.intValue()!=1 || !document.path("payload").isString())
                        throw new IllegalStateException("Invalid authorization attribute codec envelope");
                    return attributes.decode(document.path("payload").asText());
                }
                // Existing native JSON maps remain readable with the default codec.
                return attributes.decode(document.toString());
            }
        });
        return JsonMapper.builder().addModule(module).build();
    }
}
