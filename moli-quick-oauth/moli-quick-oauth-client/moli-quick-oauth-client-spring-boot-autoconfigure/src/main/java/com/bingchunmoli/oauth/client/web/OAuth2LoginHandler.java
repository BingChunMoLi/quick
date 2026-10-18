package com.bingchunmoli.oauth.client.web;

import com.bingchunmoli.oauth.client.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.core.user.OAuth2User;

/** Host creates/saves its own business authentication and writes the HTTP response. */
@FunctionalInterface
public interface OAuth2LoginHandler {
    void onLogin(OAuth2Identity identity, OAuth2User nativeUser, AuthorizedClientRef authorization,
                 HttpServletRequest request, HttpServletResponse response) throws java.io.IOException, jakarta.servlet.ServletException;
}
