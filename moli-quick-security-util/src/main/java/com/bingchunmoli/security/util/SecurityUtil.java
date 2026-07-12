package com.bingchunmoli.security.util;

import com.bingchunmoli.security.jwt.JwtUser;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Static helper for reading the current Spring Security authentication.
 *
 * @author MoLi
 */
public final class SecurityUtil {

    private static final String ROLE_PREFIX = "ROLE_";

    private SecurityUtil() {
    }

    public static Optional<Authentication> getAuthentication() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(SecurityUtil::isRealAuthentication);
    }

    public static Optional<Object> getPrincipal() {
        return getAuthentication().map(Authentication::getPrincipal);
    }

    public static Optional<Object> getLoginUser() {
        return getPrincipal();
    }

    public static <T> Optional<T> getPrincipal(Class<T> principalType) {
        if (principalType == null) {
            return Optional.empty();
        }
        return getPrincipal()
                .filter(principalType::isInstance)
                .map(principalType::cast);
    }

    public static <T> Optional<T> getLoginUser(Class<T> principalType) {
        return getPrincipal(principalType);
    }

    public static Optional<String> getUsername() {
        Optional<String> principalUsername = getPrincipal().flatMap(SecurityUtil::resolveUsername);
        return principalUsername.isPresent() ? principalUsername : getAuthentication().map(Authentication::getName);
    }

    public static String getRequiredUsername() {
        return getUsername().orElseThrow(() -> new IllegalStateException("Current user is not authenticated"));
    }

    public static Optional<String> getUserId() {
        return getPrincipal()
                .flatMap(SecurityUtil::resolveUserId);
    }

    public static String getRequiredUserId() {
        return getUserId().orElseThrow(() -> new IllegalStateException("Current user id is not available"));
    }

    public static Collection<? extends GrantedAuthority> getAuthorities() {
        return getAuthentication()
                .map(Authentication::getAuthorities)
                .orElseGet(List::of);
    }

    public static Set<String> getPermissionSet() {
        return getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    public static Set<String> getRoleSet() {
        return getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .collect(Collectors.toSet());
    }

    public static boolean isAuthenticated() {
        return getAuthentication().isPresent();
    }

    public static boolean hasAuthority(String authority) {
        if (authority == null || authority.isBlank()) {
            return false;
        }
        return getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    public static boolean hasAnyAuthority(String... authorities) {
        if (authorities == null || authorities.length == 0) {
            return false;
        }
        Set<String> authoritySet = getPermissionSet();
        for (String authority : authorities) {
            if (authoritySet.contains(authority)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasPermi(String permission) {
        return hasAuthority(permission);
    }

    public static boolean hasAnyPermi(String... permissions) {
        return hasAnyAuthority(permissions);
    }

    public static boolean hasRole(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        String authority = role.startsWith(ROLE_PREFIX) ? role : ROLE_PREFIX + role;
        return hasAuthority(authority);
    }

    public static boolean hasAnyRole(String... roles) {
        if (roles == null || roles.length == 0) {
            return false;
        }
        for (String role : roles) {
            if (hasRole(role)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAdmin(Object userId) {
        return userId != null && "1".equals(String.valueOf(userId));
    }

    public static void setAuthentication(Authentication authentication) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    public static void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static boolean isRealAuthentication(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private static Optional<String> resolveUserId(Object principal) {
        if (principal instanceof JwtUser jwtUser) {
            return Optional.ofNullable(jwtUser.getUserId());
        }
        Optional<String> userId = invokeGetter(principal, "getUserId");
        if (userId.isPresent()) {
            return userId;
        }
        return invokeGetter(principal, "getId");
    }

    private static Optional<String> resolveUsername(Object principal) {
        if (principal instanceof JwtUser jwtUser) {
            return Optional.ofNullable(jwtUser.getUsername());
        }
        return invokeGetter(principal, "getUsername");
    }

    private static Optional<String> invokeGetter(Object principal, String methodName) {
        try {
            Method method = principal.getClass().getMethod(methodName);
            Object value = method.invoke(principal);
            return Optional.ofNullable(value).map(String::valueOf);
        } catch (ReflectiveOperationException ex) {
            return Optional.empty();
        }
    }
}
