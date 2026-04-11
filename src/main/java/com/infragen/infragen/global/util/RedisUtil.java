package com.infragen.infragen.global.util;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisUtil {
    private final RedisTemplate<String, Object> redisTemplate;

    // 데이터 저장
    public void set(String key, Object value, Duration duration) {
        redisTemplate.opsForValue().set(key, value, duration);
    }

    // 데이터 조회
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    // 데이터 삭제
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    // 존재 여부 확인
    public boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    // 블랙리스트 등록 (key가 액세스 토큰, value가 "logout", ttl이 남은 유효 시간이 되도록)
    public void setBlackList(String accessToken, Long remainingTime) {
        if (remainingTime > 0) {
            redisTemplate.opsForValue().set(accessToken, "logout", remainingTime, TimeUnit.MILLISECONDS);
        }
    }

    public boolean isBlackList(String accessToken) {
        return hasKey(accessToken);
    }
}
