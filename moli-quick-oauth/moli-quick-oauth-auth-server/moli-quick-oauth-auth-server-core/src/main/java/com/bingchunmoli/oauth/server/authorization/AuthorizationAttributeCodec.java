package com.bingchunmoli.oauth.server.authorization;
import java.util.Map;
/** Explicit extension for hosts with custom principal/attribute classes. */
public interface AuthorizationAttributeCodec {
    String encode(Map<String, Object> attributes);
    Map<String, Object> decode(String data);
}
