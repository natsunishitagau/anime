package com.anime.service;

import com.anime.util.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class DanmakuRateService {

    private static final Logger log = LoggerFactory.getLogger(DanmakuRateService.class);

    private static final String USER_RATE_LIMIT_KEY_PREFIX = "danmaku:rate:user:";
    private static final String VIDEO_RATE_LIMIT_KEY_PREFIX = "danmaku:rate:video:";
    
    private static final int USER_MAX_REQUESTS_PER_SECOND = 1;
    private static final int VIDEO_MAX_REQUESTS_PER_SECOND = 20;

    private static final String RATE_LIMIT_LUA_SCRIPT = 
        "local key = KEYS[1]\n" +
        "local limit = tonumber(ARGV[1])\n" +
        "local current = redis.call('INCR', key)\n" +
        "if current == 1 then\n" +
        "    redis.call('EXPIRE', key, 1)\n" +
        "end\n" +
        "if current > limit then\n" +
        "    return 0\n" +
        "end\n" +
        "return 1";

    private final RedisUtil redisUtil;

    public DanmakuRateService(RedisUtil redisUtil) {
        this.redisUtil = redisUtil;
    }

    public boolean tryAcquire(Long userId, Long videoId) {
        boolean userAllowed = checkUserRateLimit(userId);
        if (!userAllowed) {
            log.debug("User {} exceeded rate limit", userId);
            return false;
        }

        boolean videoAllowed = checkVideoRateLimit(videoId);
        if (!videoAllowed) {
            log.debug("Video {} exceeded rate limit", videoId);
            return false;
        }

        return true;
    }

    private boolean checkUserRateLimit(Long userId) {
        String key = USER_RATE_LIMIT_KEY_PREFIX + userId;
        return executeRateLimitLua(key, USER_MAX_REQUESTS_PER_SECOND);
    }

    private boolean checkVideoRateLimit(Long videoId) {
        String key = VIDEO_RATE_LIMIT_KEY_PREFIX + videoId;
        return executeRateLimitLua(key, VIDEO_MAX_REQUESTS_PER_SECOND);
    }

    private boolean executeRateLimitLua(String key, int limit) {
        try {
            byte[] scriptBytes = RATE_LIMIT_LUA_SCRIPT.getBytes(StandardCharsets.UTF_8);
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            byte[] limitBytes = String.valueOf(limit).getBytes(StandardCharsets.UTF_8);

            Long result = redisUtil.getRedisTemplate().execute((RedisCallback<Long>) connection -> {
                return connection.eval(scriptBytes, ReturnType.INTEGER, 1, keyBytes, limitBytes);
            });

            return result != null && result == 1;
        } catch (Exception e) {
            log.error("Rate limit check failed for key: {}", key, e);
            return true;
        }
    }
}
