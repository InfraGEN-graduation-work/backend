package com.infragen.infragen.domain.auth.repository;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class EmailVerificationRepository {
    private final StringRedisTemplate redisTemplate;

    private static final DefaultRedisScript<Long> RESERVE = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[2]) == 1 then return 0 end
            local count = tonumber(redis.call('GET', KEYS[3]) or '0')
            if count >= 10 then return 0 end
            redis.call('SET', KEYS[2], ARGV[1], 'EX', 60)
            count = redis.call('INCR', KEYS[3])
            if count == 1 then redis.call('EXPIRE', KEYS[3], 3600) end
            redis.call('DEL', KEYS[1])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> STORE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[2]) ~= ARGV[1] then return 0 end
            redis.call('HSET', KEYS[1], 'digest', ARGV[2], 'attempts', 0)
            redis.call('EXPIRE', KEYS[1], 300)
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>("""
            local digest = redis.call('HGET', KEYS[1], 'digest')
            if not digest then return 0 end
            local attempts = tonumber(redis.call('HGET', KEYS[1], 'attempts') or '0')
            if attempts >= 5 then return -1 end
            if digest ~= ARGV[1] then
                attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
                if attempts >= 5 then return -1 end
                return 0
            end
            redis.call('DEL', KEYS[1])
            return 1
            """, Long.class);

    public boolean reserveSend(String emailKey, String requestId) {
        return Long.valueOf(1).equals(redisTemplate.execute(RESERVE, keys(emailKey), requestId));
    }

    public boolean storeCode(String emailKey, String requestId, String digest) {
        return Long.valueOf(1).equals(redisTemplate.execute(STORE, keys(emailKey), requestId, digest));
    }

    public long consumeCode(String emailKey, String digest) {
        Long result = redisTemplate.execute(CONSUME, List.of(keys(emailKey).getFirst()), digest);
        return result == null ? 0 : result;
    }

    private List<String> keys(String emailKey) {
        String prefix = "signup-email:{" + emailKey + "}";
        return List.of(prefix + ":code", prefix + ":cooldown", prefix + ":hour");
    }
}
