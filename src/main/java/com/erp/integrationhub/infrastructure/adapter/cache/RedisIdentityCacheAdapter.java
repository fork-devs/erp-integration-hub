package com.erp.integrationhub.infrastructure.adapter.cache;

import com.erp.integrationhub.domain.model.ConsultaPlacaResult;
import com.erp.integrationhub.domain.model.ConsultaRucResult;
import com.erp.integrationhub.domain.port.IdentityCachePort;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.Optional;

public class RedisIdentityCacheAdapter implements IdentityCachePort {

    private static final Logger log = LoggerFactory.getLogger(RedisIdentityCacheAdapter.class);

    private static final String RUC_KEY_PREFIX = "erp:cache:ruc:";
    private static final String PLACA_KEY_PREFIX = "erp:cache:placa:";
    private static final Duration RUC_TTL = Duration.ofDays(30);
    private static final Duration PLACA_TTL = Duration.ofDays(7);

    private final RedisTemplate<String, Object> redisTemplate;

    // L1 in-memory fallback cache
    private final Cache<String, ConsultaRucResult> localRucCache;
    private final Cache<String, ConsultaPlacaResult> localPlacaCache;

    public RedisIdentityCacheAdapter(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.localRucCache = Caffeine.newBuilder()
                .expireAfterWrite(RUC_TTL)
                .maximumSize(10000)
                .build();
        this.localPlacaCache = Caffeine.newBuilder()
                .expireAfterWrite(PLACA_TTL)
                .maximumSize(10000)
                .build();
    }

    @Override
    public Optional<ConsultaRucResult> getRuc(String ruc) {
        // 1. Try Redis
        if (redisTemplate != null) {
            try {
                Object cached = redisTemplate.opsForValue().get(RUC_KEY_PREFIX + ruc);
                if (cached instanceof ConsultaRucResult result) {
                    log.debug("[Redis] Cache HIT para RUC {}", ruc);
                    return Optional.of(result);
                }
            } catch (Exception e) {
                log.debug("[Redis] No disponible ({}), usando L1 cache para RUC {}", e.getMessage(), ruc);
            }
        }

        // 2. Fallback to local memory cache
        ConsultaRucResult localResult = localRucCache.getIfPresent(ruc);
        return Optional.ofNullable(localResult);
    }

    @Override
    public void putRuc(String ruc, ConsultaRucResult result) {
        localRucCache.put(ruc, result);
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(RUC_KEY_PREFIX + ruc, result, RUC_TTL);
                log.debug("[Redis] Guardado RUC {} con TTL 30 días", ruc);
            } catch (Exception e) {
                log.debug("[Redis] No se pudo persistir RUC {} en Redis: {}", ruc, e.getMessage());
            }
        }
    }

    @Override
    public Optional<ConsultaPlacaResult> getPlaca(String placa) {
        // 1. Try Redis
        if (redisTemplate != null) {
            try {
                Object cached = redisTemplate.opsForValue().get(PLACA_KEY_PREFIX + placa);
                if (cached instanceof ConsultaPlacaResult result) {
                    log.debug("[Redis] Cache HIT para Placa {}", placa);
                    return Optional.of(result);
                }
            } catch (Exception e) {
                log.debug("[Redis] No disponible ({}), usando L1 cache para Placa {}", e.getMessage(), placa);
            }
        }

        // 2. Fallback to local memory cache
        ConsultaPlacaResult localResult = localPlacaCache.getIfPresent(placa);
        return Optional.ofNullable(localResult);
    }

    @Override
    public void putPlaca(String placa, ConsultaPlacaResult result) {
        localPlacaCache.put(placa, result);
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(PLACA_KEY_PREFIX + placa, result, PLACA_TTL);
                log.debug("[Redis] Guardada Placa {} con TTL 7 días", placa);
            } catch (Exception e) {
                log.debug("[Redis] No se pudo persistir Placa {} en Redis: {}", placa, e.getMessage());
            }
        }
    }
}
