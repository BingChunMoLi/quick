package com.bingchunmoli.oauth.client.web;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import static org.assertj.core.api.Assertions.*;

class SessionAuthorizationRequestRepositoryTest {
    @Test void concurrentCallbacksCanConsumeTheSameStateOnlyOnce() throws Exception {
        var repository = new SessionAuthorizationRequestRepository(Duration.ofMinutes(1));
        var start = new MockHttpServletRequest();
        repository.saveAuthorizationRequest(authorization("shared"), start, new MockHttpServletResponse());
        var session = start.getSession(false);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        var barrier = new CyclicBarrier(8);
        try {
            var callbacks = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 8; i++) callbacks.add(pool.submit(() -> {
                var request = new MockHttpServletRequest(); request.setSession(session); request.setParameter("state", "shared");
                barrier.await(5, TimeUnit.SECONDS);
                return repository.removeAuthorizationRequest(request, new MockHttpServletResponse()) != null;
            }));
            int consumed = 0;
            for (var callback : callbacks) if (callback.get(5, TimeUnit.SECONDS)) consumed++;
            assertThat(consumed).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void consumedStateFreesCapacityForAnotherTab() {
        var repository = new SessionAuthorizationRequestRepository(Duration.ofMinutes(1));
        var request = new MockHttpServletRequest(); var response = new MockHttpServletResponse();
        for (int i = 0; i < 20; i++) repository.saveAuthorizationRequest(authorization("state-" + i), request, response);
        assertThatThrownBy(() -> repository.saveAuthorizationRequest(authorization("next"), request, response))
                .hasMessageContaining("Too many pending");
        request.setParameter("state", "state-0");
        assertThat(repository.removeAuthorizationRequest(request, response)).isNotNull();
        repository.saveAuthorizationRequest(authorization("next"), request, response);
        request.setParameter("state", "next");
        assertThat(repository.removeAuthorizationRequest(request, response)).isNotNull();
    }

    @Test void expiredStateCannotBeConsumedAndDoesNotPreventANewAttempt() throws Exception {
        var repository = new SessionAuthorizationRequestRepository(Duration.ofMillis(100));
        var request = new MockHttpServletRequest(); var response = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(authorization("expired"), request, response);
        Thread.sleep(160);
        request.setParameter("state", "expired");
        assertThat(repository.removeAuthorizationRequest(request, response)).isNull();
        repository.saveAuthorizationRequest(authorization("new"), request, response);
        request.setParameter("state", "new");
        assertThat(repository.loadAuthorizationRequest(request).getState()).isEqualTo("new");
    }

    private static OAuth2AuthorizationRequest authorization(String state) {
        return OAuth2AuthorizationRequest.authorizationCode().clientId("id").authorizationUri("https://provider/authorize")
                .redirectUri("https://host/callback").state(state).build();
    }
}
