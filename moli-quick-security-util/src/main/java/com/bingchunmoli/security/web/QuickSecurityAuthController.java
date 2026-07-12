package com.bingchunmoli.security.web;

import com.bingchunmoli.security.jwt.JwtToken;
import com.bingchunmoli.security.jwt.JwtTokenService;
import com.bingchunmoli.security.logout.SecurityLogoutService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * JSON endpoints for quick Spring Security integration.
 *
 * @author MoLi
 */
@RestController
@RequestMapping("${moli.security.auth-base-path:/security}")
public class QuickSecurityAuthController {

    private final AuthenticationManager authenticationManager;
    private final SessionRegistry sessionRegistry;
    private final SecurityLogoutService logoutService;
    private final JwtTokenService jwtTokenService;

    public QuickSecurityAuthController(AuthenticationManager authenticationManager,
                                       SessionRegistry sessionRegistry,
                                       SecurityLogoutService logoutService,
                                       JwtTokenService jwtTokenService) {
        this.authenticationManager = authenticationManager;
        this.sessionRegistry = sessionRegistry;
        this.logoutService = logoutService;
        this.jwtTokenService = jwtTokenService;
    }

    @PostMapping("/login")
    public ResponseEntity<QuickSecurityResponse<LoginUserInfo>> login(@RequestBody LoginRequest loginRequest,
                                                                      HttpServletRequest request) {
        if (loginRequest == null || isBlank(loginRequest.getUsername()) || isBlank(loginRequest.getPassword())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(QuickSecurityResponse.fail("username and password must not be blank"));
        }
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            loginRequest.getUsername(), loginRequest.getPassword()));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            JwtToken token = jwtTokenService.createToken(toJwtUser(authentication));
            LoginUserInfo loginUserInfo = LoginUserInfo.of(authentication, token.getSessionId());
            loginUserInfo.setToken(token.getToken());
            loginUserInfo.setTokenType(token.getTokenType());
            loginUserInfo.setExpiresAt(token.getExpiresAt());
            loginUserInfo.setMode(token.getMode().name());

            return ResponseEntity.ok(QuickSecurityResponse.ok(loginUserInfo));
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(QuickSecurityResponse.fail("authentication failed"));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<QuickSecurityResponse<LoginUserInfo>> currentUser(HttpServletRequest request) {
        return com.bingchunmoli.security.util.SecurityUtil.getAuthentication()
                .map(authentication -> {
                    HttpSession session = request.getSession(false);
                    String sessionId = session == null ? null : session.getId();
                    return ResponseEntity.ok(QuickSecurityResponse.ok(LoginUserInfo.of(authentication, sessionId)));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(QuickSecurityResponse.fail("not authenticated")));
    }

    @PostMapping("/logout")
    public QuickSecurityResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        logoutService.logoutCurrent(request, response);
        return QuickSecurityResponse.ok(null);
    }

    @PostMapping("/force-logout/{username}")
    public QuickSecurityResponse<ForceLogoutResult> forceLogoutByUsername(@PathVariable String username) {
        int expiredCount = logoutService.forceLogoutByUsername(username);
        expiredCount += jwtTokenService.forceLogoutByUserId(username);
        return QuickSecurityResponse.ok(new ForceLogoutResult(username, expiredCount));
    }

    @PostMapping("/force-logout-token")
    public QuickSecurityResponse<Void> forceLogoutToken(@RequestBody ForceLogoutTokenRequest request) {
        if (request != null && request.getToken() != null && !request.getToken().isBlank()) {
            jwtTokenService.forceLogoutToken(request.getToken());
        }
        return QuickSecurityResponse.ok(null);
    }

    private com.bingchunmoli.security.jwt.JwtUser toJwtUser(Authentication authentication) {
        return new com.bingchunmoli.security.jwt.JwtUser(
                authentication.getName(),
                authentication.getName(),
                authentication.getAuthorities().stream()
                        .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                        .toList(),
                java.util.Map.of());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
