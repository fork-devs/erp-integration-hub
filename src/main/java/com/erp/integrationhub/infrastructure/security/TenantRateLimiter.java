package com.erp.integrationhub.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-tenant rate limiter for external identity and vehicle queries (SUNAT/SUNARP).
 *
 * <p>Prevents noisy-neighbor starvation and quota exhaustion on upstream identity providers.
 */
@Component
public class TenantRateLimiter {

    private final int maxPerWindow;
    private final Duration windowDuration;
    private final Map<UUID, RateWindow> windows = new ConcurrentHashMap<>();

    public TenantRateLimiter(
            @Value("${erp.integration.identity-rate-limit.max:60}") int maxPerWindow,
            @Value("${erp.integration.identity-rate-limit.window-seconds:60}") long windowSeconds) {
        this.maxPerWindow = maxPerWindow;
        this.windowDuration = Duration.ofSeconds(windowSeconds);
    }

    /**
     * Attempts to acquire one permit for the given tenant within the current sliding window.
     *
     * @param tenantId workshop/tenant identifier
     * @return true if permit is granted, false if rate limit exceeded
     */
    public boolean tryAcquire(UUID tenantId) {
        if (tenantId == null) {
            return true;
        }
        Instant now = Instant.now();
        windows.compute(tenantId, (tenant, existing) -> {
            if (existing == null || existing.expiresAt.isBefore(now)) {
                return new RateWindow(now.plus(this.windowDuration), new AtomicInteger(1));
            }
            existing.counter.incrementAndGet();
            return existing;
        });
        RateWindow window = windows.get(tenantId);
        return window != null && window.counter.get() <= maxPerWindow;
    }

    /**
     * Calculates the remaining seconds before the tenant rate window resets.
     *
     * @param tenantId workshop/tenant identifier
     * @return seconds to wait, or 0 if within limits
     */
    public long waitSeconds(UUID tenantId) {
        if (tenantId == null) {
            return 0;
        }
        RateWindow window = windows.get(tenantId);
        if (window == null) {
            return 0;
        }
        long remaining = Duration.between(Instant.now(), window.expiresAt).getSeconds();
        return Math.max(remaining, 1);
    }

    // --- Backward compatibility methods ---

    public boolean intentar(UUID tenantId) {
        return tryAcquire(tenantId);
    }

    public long segundosDeEspera(UUID tenantId) {
        return waitSeconds(tenantId);
    }

    private static final class RateWindow {
        private final Instant expiresAt;
        private final AtomicInteger counter;

        private RateWindow(Instant expiresAt, AtomicInteger counter) {
            this.expiresAt = expiresAt;
            this.counter = counter;
        }
    }
}
