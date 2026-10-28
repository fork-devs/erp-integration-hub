package com.erp.integrationhub.infrastructure.adapter.cache;

import com.erp.integrationhub.domain.model.QueryPlateResult;
import com.erp.integrationhub.domain.model.QueryRucResult;
import com.erp.integrationhub.domain.port.IdentityCachePort;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.Optional;

/**
 * Two-tier cache adapter (L1 in-memory Caffeine + L2 distributed Redis) for identity validation queries.
 */
public class RedisIdentityCacheAdapter implements IdentityCachePort {

    private static final Logger log = LoggerFactory.getLogger(RedisIdentityCacheAdapter.class);

    private static final String RUC_KEY_PREFIX = "erp:cache:ruc:";
    private static final String PLACA_KEY_PREFIX = "erp:cache:placa:";
    private static final Duration RUC_TTL = Duration.ofDays(30);
    private static final Duration PLACA_TTL = Duration.ofDays(7);

    private final RedisTemplate<String, Object> redisTemplate;

    // L1 in-memory fallback cache
    private final Cache<String, QueryRucResult> localRucCache;
    private final Cache<String, QueryPlateResult> localPlateCache;

    public RedisIdentityCacheAdapter(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.localRucCache = Caffeine.newBuilder()
                .expireAfterWrite(RUC_TTL)
                .maximumSize(10000)
                .build();
        this.localPlateCache = Caffeine.newBuilder()
                .expireAfterWrite(PLACA_TTL)
                .maximumSize(10000)
                .build();
    }

    @Override
    public Optional<QueryRucResult> getRuc(String ruc) {
        // 1. Try Redis
        if (redisTemplate != null) {
            try {
                Object cached = redisTemplate.opsForValue().get(RUC_KEY_PREFIX + ruc);
                if (cached instanceof QueryRucResult result) {
                    log.debug("[Redis] Cache HIT for RUC {}", ruc);
                    return Optional.of(result);
                }
            } catch (Exception e) {
                log.debug("[Redis] Unavailable ({}), falling back to L1 cache for RUC {}", e.getMessage(), ruc);
            }
        }

        // 2. Fallback to local memory cache
        QueryRucResult localResult = localRucCache.getIfPresent(ruc);
        return Optional.ofNullable(localResult);
    }

    @Override
    public void putRuc(String ruc, QueryRucResult result) {
        localRucCache.put(ruc, result);
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(RUC_KEY_PREFIX + ruc, result, RUC_TTL);
                log.debug("[Redis] Saved RUC {} with TTL 30 days", ruc);
            } catch (Exception e) {
                log.debug("[Redis] Could not persist RUC {} to Redis: {}", ruc, e.getMessage());
            }
        }
    }

    @Override
    public Optional<QueryPlateResult> getPlaca(String plate) {
        // 1. Try Redis
        if (redisTemplate != null) {
            try {
                Object cached = redisTemplate.opsForValue().get(PLACA_KEY_PREFIX + plate);
                if (cached instanceof QueryPlateResult result) {
                    log.debug("[Redis] Cache HIT for plate {}", plate);
                    return Optional.of(result);
                }
            } catch (Exception e) {
                log.debug("[Redis] Unavailable ({}), falling back to L1 cache for plate {}", e.getMessage(), plate);
            }
        }

        // 2. Fallback to local memory cache
        QueryPlateResult localResult = localPlateCache.getIfPresent(plate);
        return Optional.ofNullable(localResult);
    }

    @Override
    public void putPlaca(String plate, QueryPlateResult result) {
        localPlateCache.put(plate, result);
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(PLACA_KEY_PREFIX + plate, result, PLACA_TTL);
                log.debug("[Redis] Saved plate {} with TTL 7 days", plate);
            } catch (Exception e) {
                log.debug("[Redis] Could not persist plate {} to Redis: {}", plate, e.getMessage());
            }
        }
    }
}
