package com.bingchunmoli.oauth.server.authorization;
import java.util.*;
import org.springframework.security.jackson.SecurityJacksonModules;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
/** Uses Spring Security's explicitly allowlisted polymorphic modules, never arbitrary default typing. */
public final class SecurityAuthorizationAttributeCodec implements AuthorizationAttributeCodec {
    private final JsonMapper json = JsonMapper.builder().addModules(SecurityJacksonModules.getModules(getClass().getClassLoader())).build();
    @Override public String encode(Map<String, Object> attributes) {
        validateCredentials(attributes,Collections.newSetFromMap(new IdentityHashMap<>()));
        try {
            String encoded=json.writeValueAsString(normalize(attributes));
            json.readValue(encoded,new TypeReference<Map<String,Object>>() { });
            return encoded;
        }
        catch (RuntimeException error) { throw new IllegalStateException("Unsupported authorization attributes; provide AuthorizationAttributeCodec"); }
    }
    private static void validateCredentials(Object value,Set<Object> path) {
        if(!(value instanceof Map<?,?> || value instanceof Collection<?> || value instanceof Object[] ||
                value instanceof Authentication || value instanceof UserDetails || value instanceof OAuth2AuthenticatedPrincipal))return;
        if(!path.add(value))throw new IllegalStateException("Cyclic authorization attributes are unsupported");
        try {
            if(value instanceof Authentication authentication) {
                Object credentials=authentication.getCredentials();
                if(credentials!=null && !(credentials instanceof String text && text.isEmpty()))
                    throw new IllegalStateException("Erase authentication credentials before persisting authorization");
                validateCredentials(authentication.getPrincipal(),path);
                validateCredentials(authentication.getDetails(),path);
            }
            if(value instanceof UserDetails user && user.getPassword()!=null)
                throw new IllegalStateException("Erase user credentials before persisting authorization");
            if(value instanceof OAuth2AuthenticatedPrincipal principal)validateCredentials(principal.getAttributes(),path);
            if(value instanceof Map<?,?> map)for(Object item:map.values())validateCredentials(item,path);
            if(value instanceof Collection<?> collection)for(Object item:collection)validateCredentials(item,path);
            if(value instanceof Object[] array)for(Object item:array)validateCredentials(item,path);
        } finally {path.remove(value);}
    }
    private static Object normalize(Object value) {
        if(value instanceof Map<?,?> map) {
            Map<String,Object> result=new LinkedHashMap<>();map.forEach((key,item)->result.put((String)key,normalize(item)));return result;
        }
        if(value instanceof Set<?> set) {
            Set<Object> result=new LinkedHashSet<>();set.forEach(item->result.add(normalize(item)));return result;
        }
        if(value instanceof Collection<?> list) {
            List<Object> result=new ArrayList<>();list.forEach(item->result.add(normalize(item)));return result;
        }
        return value;
    }
    @Override public Map<String, Object> decode(String data) {
        try {
            Map<String,Object> decoded=json.readValue(data,new TypeReference<Map<String,Object>>() { });
            validateCredentials(decoded,Collections.newSetFromMap(new IdentityHashMap<>()));return decoded;
        }
        catch (RuntimeException error) { throw new IllegalStateException("Invalid authorization attributes"); }
    }
}
