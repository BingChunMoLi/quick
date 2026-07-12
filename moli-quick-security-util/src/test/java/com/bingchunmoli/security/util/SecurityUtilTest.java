package com.bingchunmoli.security.util;

import com.bingchunmoli.security.jwt.JwtUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityUtilTest {

    @AfterEach
    void tearDown() {
        SecurityUtil.clearContext();
    }

    @Test
    void shouldReadCurrentAuthenticatedUser() {
        JwtUser principal = new JwtUser("1001", "moli", List.of("ROLE_ADMIN", "system:read"), Map.of());
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, "password",
                        AuthorityUtils.createAuthorityList("ROLE_ADMIN", "system:read"));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(SecurityUtil.isAuthenticated()).isTrue();
        assertThat(SecurityUtil.getUsername()).contains("moli");
        assertThat(SecurityUtil.getPrincipal(JwtUser.class)).containsSame(principal);
        assertThat(SecurityUtil.getLoginUser(JwtUser.class)).containsSame(principal);
        assertThat(SecurityUtil.getUserId()).contains("1001");
        assertThat(SecurityUtil.getRoleSet()).containsExactly("ADMIN");
        assertThat(SecurityUtil.getPermissionSet()).contains("ROLE_ADMIN", "system:read");
        assertThat(SecurityUtil.hasRole("ADMIN")).isTrue();
        assertThat(SecurityUtil.hasRole("ROLE_ADMIN")).isTrue();
        assertThat(SecurityUtil.hasAnyRole("USER", "ADMIN")).isTrue();
        assertThat(SecurityUtil.hasAuthority("system:read")).isTrue();
        assertThat(SecurityUtil.hasPermi("system:read")).isTrue();
        assertThat(SecurityUtil.hasAnyPermi("system:write", "system:read")).isTrue();
        assertThat(SecurityUtil.isAdmin(1L)).isTrue();
    }

    @Test
    void shouldIgnoreAnonymousAuthentication() {
        AnonymousAuthenticationToken authentication = new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(SecurityUtil.isAuthenticated()).isFalse();
        assertThat(SecurityUtil.getAuthentication()).isEmpty();
        assertThat(SecurityUtil.getUsername()).isEmpty();
    }
}
