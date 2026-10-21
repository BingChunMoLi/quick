package com.bingchunmoli.security.autoconfigure;

import com.bingchunmoli.security.logout.SecurityLogoutService;
import jakarta.servlet.http.HttpSessionBindingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SessionRegistrationLogoutRaceTest {
    private static final String CONTEXT = HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
    enum LogoutKind { SESSION, PRINCIPAL, USERNAME }

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @CsvSource({"missing,SESSION", "different,SESSION", "same,SESSION", "missing,PRINCIPAL", "different,PRINCIPAL",
            "same,PRINCIPAL", "missing,USERNAME", "different,USERNAME", "same,USERNAME"})
    void aPendingRegistrationCannotReplaceAnExpiredSession(String initial, LogoutKind kind) throws Exception {
        var registry = new PausedRegistry("information");
        var session = session("alice");
        if (!initial.equals("missing")) registry.registerNewSession(session.getId(), initial.equals("same") ? "alice" : "before");
        var listener = new SecuritySessionRegistrationListener(registry);
        var service = new SecurityLogoutService(registry, new SecurityContextLogoutHandler());
        var expiredCount = new AtomicInteger();

        race(registry, () -> save(listener, session), () -> {
            save(listener, session);
            expiredCount.set(expire(service, kind, session.getId()));
        });

        assertThat(expiredCount.get()).isEqualTo(1);
        assertExpiredAndRejected(registry, session);
    }

    @ParameterizedTest
    @EnumSource(LogoutKind.class)
    void logoutSnapshotCannotLoseTheSessionToConcurrentPrincipalReplacement(LogoutKind kind) throws Exception {
        String point = switch (kind) {
            case SESSION -> "information";
            case PRINCIPAL -> "sessions";
            case USERNAME -> "principals";
        };
        var registry = new PausedRegistry(point);
        var session = session("bob");
        registry.registerNewSession(session.getId(), "alice");
        var listener = new SecuritySessionRegistrationListener(registry);
        var service = new SecurityLogoutService(registry, new SecurityContextLogoutHandler());
        var expiredCount = new AtomicInteger();

        race(registry, () -> expiredCount.set(expire(service, kind, session.getId())), () -> save(listener, session));

        assertThat(expiredCount.get()).isEqualTo(1);
        assertExpiredAndRejected(registry, session);
    }

    @ParameterizedTest
    @EnumSource(LogoutKind.class)
    void callbacksAfterACompletedLogoutCannotReviveTheSession(LogoutKind kind) throws Exception {
        var registry = new SessionRegistryImpl();
        var session = session("alice");
        var listener = new SecuritySessionRegistrationListener(registry);
        save(listener, session);
        var service = new SecurityLogoutService(registry, new SecurityContextLogoutHandler());
        assertThat(expire(service, kind, session.getId())).isEqualTo(1);
        save(listener, session);
        session.setAttribute(CONTEXT, new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated("bob", null, List.of())));
        listener.attributeReplaced(new HttpSessionBindingEvent(session, CONTEXT));
        assertExpiredAndRejected(registry, session);
    }

    private static void race(PausedRegistry registry, Runnable pausedAction, Runnable competingAction) throws Exception {
        var failure = new AtomicReference<Throwable>();
        Thread paused = thread("paused-operation", pausedAction, failure);
        Thread competitor = thread("competing-operation", competingAction, failure);
        paused.start();
        try {
            assertThat(registry.snapshotRead.await(5, TimeUnit.SECONDS)).isTrue();
            competitor.start();
            // Allow either serialization or an atomic nonblocking implementation. On the buggy
            // version the competitor finishes before the old snapshot resumes, reproducing the loss.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (competitor.isAlive() && competitor.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            assertThat(competitor.isAlive() && competitor.getState() != Thread.State.BLOCKED).isFalse();
        } finally {
            registry.resume.countDown();
            paused.join(5_000);
            competitor.join(5_000);
        }
        assertThat(paused.isAlive()).isFalse();
        assertThat(competitor.isAlive()).isFalse();
        assertThat(failure.get()).isNull();
    }

    private static Thread thread(String name, Runnable action, AtomicReference<Throwable> failure) {
        return new Thread(() -> {
            try { action.run(); } catch (Throwable error) { failure.compareAndSet(null, error); }
        }, name);
    }

    private static int expire(SecurityLogoutService service, LogoutKind kind, String sessionId) {
        return switch (kind) {
            case SESSION -> service.forceLogoutSession(sessionId) ? 1 : 0;
            case PRINCIPAL -> service.forceLogout("alice");
            case USERNAME -> service.forceLogoutByUsername("alice");
        };
    }

    private static MockHttpSession session(String principal) {
        var session = new MockHttpSession();
        session.setAttribute(CONTEXT, new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of())));
        return session;
    }

    private static void save(SecuritySessionRegistrationListener listener, MockHttpSession session) {
        listener.attributeAdded(new HttpSessionBindingEvent(session, CONTEXT, session.getAttribute(CONTEXT)));
    }

    private static void assertExpiredAndRejected(SessionRegistryImpl registry, MockHttpSession session) throws Exception {
        assertThat(registry.getSessionInformation(session.getId()).isExpired()).isTrue();
        var request = new MockHttpServletRequest();
        request.setSession(session);
        var response = new MockHttpServletResponse();
        var proceeded = new AtomicBoolean();
        new SecurityForceLogoutFilter(registry, new SecurityContextLogoutHandler(), 401)
                .doFilter(request, response, (req, res) -> proceeded.set(true));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(proceeded.get()).isFalse();
        assertThat(session.isInvalid()).isTrue();
    }

    private static class PausedRegistry extends SessionRegistryImpl {
        private final String point;
        private final AtomicBoolean once = new AtomicBoolean();
        private final CountDownLatch snapshotRead = new CountDownLatch(1);
        private final CountDownLatch resume = new CountDownLatch(1);

        PausedRegistry(String point) { this.point = point; }

        private void pause(String currentPoint) {
            if (point.equals(currentPoint) && Thread.currentThread().getName().equals("paused-operation") && once.compareAndSet(false, true)) {
                snapshotRead.countDown();
                try {
                    if (!resume.await(10, TimeUnit.SECONDS)) throw new AssertionError("Snapshot was not resumed");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
            }
        }

        @Override public SessionInformation getSessionInformation(String id) {
            var snapshot = super.getSessionInformation(id);
            pause("information");
            return snapshot;
        }
        @Override public List<SessionInformation> getAllSessions(Object principal, boolean includeExpiredSessions) {
            var snapshot = super.getAllSessions(principal, includeExpiredSessions);
            pause("sessions");
            return snapshot;
        }
        @Override public List<Object> getAllPrincipals() {
            var snapshot = super.getAllPrincipals();
            pause("principals");
            return snapshot;
        }
    }
}
