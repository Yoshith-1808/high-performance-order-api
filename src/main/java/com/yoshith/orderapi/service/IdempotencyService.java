package com.yoshith.orderapi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final StringRedisTemplate redisTemplate;
    private static final String IDEMPOTENCY_PREFIX = "order:idemp:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    /**
     * Attempts to acquire an idempotency lock for the given key.
     * Returns true if this is the first time the key is seen (lock acquired).
     * Returns false if key was previously submitted.
     */
    public boolean acquireLock(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return true; // No key provided, bypass idempotency check
        }
        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(redisKey, "PROCESSING", DEFAULT_TTL);
        log.debug("Idempotency lock check for key [{}]: acquired={}", idempotencyKey, acquired);
        return Boolean.TRUE.equals(acquired);
    }

    public void releaseLock(String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            redisTemplate.delete(IDEMPOTENCY_PREFIX + idempotencyKey);
        }
    }

    public void markSuccess(String idempotencyKey, String orderTrackingNumber) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            redisTemplate.opsForValue().set(IDEMPOTENCY_PREFIX + idempotencyKey, orderTrackingNumber, DEFAULT_TTL);
        }
    }
}
