package com.anime.service;

import java.time.Instant;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * 弹幕限流服务 — 三级精细化限流策略：
 * <ul>
 *   <li><b>用户级 — 令牌桶（Token Bucket）</b>：允许短时突发，长期限速平滑。适合单用户偶尔连发的场景。</li>
 *   <li><b>房间级 — 滑动窗口（Sliding Window）</b>：精确控制时间窗口内请求数，无固定边界抖动。适合保护房间级吞吐。</li>
 *   <li><b>IP 级 — 漏桶（Leaky Bucket）</b>：恒定速率处理，超出即丢弃，天然削峰填谷。适合防御单 IP 刷量 / 共享 NAT 场景。</li>
 * </ul>
 * 三级全通过才放行，任一拒绝返回 false。
 */
@Service
public class DanmakuRateService {

    private static final Logger log = LoggerFactory.getLogger(DanmakuRateService.class);

    // ──────────────────────────── Redis Key 前缀 ────────────────────────────
    private static final String TOKEN_BUCKET_KEY = "danmaku:token:user:";
    private static final String SLIDING_WINDOW_KEY = "danmaku:window:video:";
    private static final String LEAKY_BUCKET_KEY = "danmaku:leaky:ip:";

    // ──────────────────────────── 默认参数 ────────────────────────────
    /** 令牌桶：最大令牌数 */
    @Value("${danmaku.rate-limit.user.capacity:5}")
    private int userCapacity;

    /** 令牌桶：每秒补充令牌数 */
    @Value("${danmaku.rate-limit.user.refill-rate:1.0}")
    private double userRefillRate;

    /** 滑动窗口：窗口大小（秒） */
    @Value("${danmaku.rate-limit.room.window-seconds:1}")
    private int roomWindowSeconds;

    /** 滑动窗口：窗口内最大请求数 */
    @Value("${danmaku.rate-limit.room.max-requests:20}")
    private int roomMaxRequests;

    /** 漏桶：最大容量 */
    @Value("${danmaku.rate-limit.ip.capacity:10}")
    private int ipCapacity;

    /** 漏桶：每秒漏出速率 */
    @Value("${danmaku.rate-limit.ip.leak-rate:5.0}")
    private double ipLeakRate;

    // ──────────────────────────── Lua 脚本 ────────────────────────────

    /**
     * 令牌桶 Lua 脚本。
     * KEYS[1] — 桶的 Redis key
     * ARGV[1] — 当前时间戳（毫秒）
     * ARGV[2] — 桶容量
     * ARGV[3] — 每秒补充速率
     * 返回 1=放行, 0=拒绝
     */
    private static final String TOKEN_BUCKET_SCRIPT = """
        local key = KEYS[1]
        local now = tonumber(ARGV[1])
        local capacity = tonumber(ARGV[2])
        local refillRate = tonumber(ARGV[3])

        local data = redis.call('HMGET', key, 'tokens', 'lastRefill')
        local tokens = tonumber(data[1])
        local lastRefill = tonumber(data[2])

        if tokens == nil then
            tokens = capacity
            lastRefill = now
        end

        -- 计算补充
        local elapsed = (now - lastRefill) / 1000.0
        local refill = elapsed * refillRate
        tokens = math.min(capacity, tokens + refill)

        if tokens >= 1.0 then
            tokens = tokens - 1.0
            redis.call('HMSET', key, 'tokens', tokens, 'lastRefill', now)
            redis.call('EXPIRE', key, math.ceil(capacity / refillRate) * 2 + 10)
            return 1
        end

        -- 即使拒绝也更新 lastRefill，防止时间回退导致 refill 虚高
        redis.call('HMSET', key, 'tokens', tokens, 'lastRefill', now)
        redis.call('EXPIRE', key, math.ceil(capacity / refillRate) * 2 + 10)
        return 0
        """;

    /**
     * 滑动窗口 Lua 脚本（基于 Sorted Set）。
     * KEYS[1] — 窗口的 Redis key
     * ARGV[1] — 当前时间戳（毫秒）
     * ARGV[2] — 窗口大小（毫秒）
     * ARGV[3] — 最大请求数
     * ARGV[4] — 本次请求的唯一 member（时间戳 + 随机数）
     * 返回 1=放行, 0=拒绝
     */
    private static final String SLIDING_WINDOW_SCRIPT = """
        local key = KEYS[1]
        local now = tonumber(ARGV[1])
        local windowMs = tonumber(ARGV[2])
        local maxRequests = tonumber(ARGV[3])
        local member = ARGV[4]

        local windowStart = now - windowMs
        redis.call('ZREMRANGEBYSCORE', key, 0, windowStart)

        local count = redis.call('ZCARD', key)
        if count < maxRequests then
            redis.call('ZADD', key, now, member)
            redis.call('EXPIRE', key, math.ceil(windowMs / 1000) + 2)
            return 1
        end

        return 0
        """;

    /**
     * 漏桶 Lua 脚本。
     * KEYS[1] — 桶的 Redis key
     * ARGV[1] — 当前时间戳（毫秒）
     * ARGV[2] — 桶容量
     * ARGV[3] — 每秒漏出速率
     * 返回 1=放行, 0=拒绝
     */
    private static final String LEAKY_BUCKET_SCRIPT = """
        local key = KEYS[1]
        local now = tonumber(ARGV[1])
        local capacity = tonumber(ARGV[2])
        local leakRate = tonumber(ARGV[3])

        local data = redis.call('HMGET', key, 'water', 'lastLeak')
        local water = tonumber(data[1])
        local lastLeak = tonumber(data[2])

        if water == nil then
            water = 0
            lastLeak = now
        end

        -- 漏水
        local elapsed = (now - lastLeak) / 1000.0
        local leaked = elapsed * leakRate
        water = math.max(0, water - leaked)

        if water < capacity then
            water = water + 1
            redis.call('HMSET', key, 'water', water, 'lastLeak', now)
            redis.call('EXPIRE', key, math.ceil(capacity / leakRate) * 2 + 10)
            return 1
        end

        redis.call('HMSET', key, 'water', water, 'lastLeak', now)
        redis.call('EXPIRE', key, math.ceil(capacity / leakRate) * 2 + 10)
        return 0
        """;

    // ──────────────────────────── 预编译脚本 ────────────────────────────
    private final DefaultRedisScript<Long> tokenBucketScript;
    private final DefaultRedisScript<Long> slidingWindowScript;
    private final DefaultRedisScript<Long> leakyBucketScript;
    private final StringRedisTemplate redisTemplate;

    public DanmakuRateService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;

        this.tokenBucketScript = new DefaultRedisScript<>();
        tokenBucketScript.setScriptText(TOKEN_BUCKET_SCRIPT);
        tokenBucketScript.setResultType(Long.class);

        this.slidingWindowScript = new DefaultRedisScript<>();
        slidingWindowScript.setScriptText(SLIDING_WINDOW_SCRIPT);
        slidingWindowScript.setResultType(Long.class);

        this.leakyBucketScript = new DefaultRedisScript<>();
        leakyBucketScript.setScriptText(LEAKY_BUCKET_SCRIPT);
        leakyBucketScript.setResultType(Long.class);
    }

    /**
     * 三级限流总入口。
     *
     * @param userId  用户 ID（已登录用户必传）
     * @param videoId 视频/房间 ID
     * @param ip      客户端真实 IP
     * @return true=放行, false=被限流
     */
    public boolean tryAcquire(Long userId, Long videoId, String ip) {
        long now = Instant.now().toEpochMilli();

        // ① 用户级 — 令牌桶
        if (!checkTokenBucket(userId, now)) {
            log.debug("Token bucket rejected: userId={}", userId);
            return false;
        }

        // ② 房间级 — 滑动窗口
        if (!checkSlidingWindow(videoId, now)) {
            log.debug("Sliding window rejected: videoId={}", videoId);
            return false;
        }

        // ③ IP 级 — 漏桶
        if (ip != null && !ip.isBlank()) {
            if (!checkLeakyBucket(ip, now)) {
                log.debug("Leaky bucket rejected: ip={}", ip);
                return false;
            }
        }

        return true;
    }

    // ──────────────────────────── 各策略实现 ────────────────────────────

    private boolean checkTokenBucket(Long userId, long now) {
        try {
            String key = TOKEN_BUCKET_KEY + userId;
            Long result = redisTemplate.execute(
                    tokenBucketScript,
                    Collections.singletonList(key),
                    String.valueOf(now),
                    String.valueOf(userCapacity),
                    String.valueOf(userRefillRate));
            return result != null && result == 1L;
        } catch (Exception e) {
            log.error("Token bucket check failed: userId={}", userId);
            return true; // 降级放行
        }
    }

    private boolean checkSlidingWindow(Long videoId, long now) {
        try {
            String key = SLIDING_WINDOW_KEY + videoId;
            String member = now + ":" + Thread.currentThread().getId() + ":" + Math.random();
            long windowMs = roomWindowSeconds * 1000L;
            Long result = redisTemplate.execute(
                    slidingWindowScript,
                    Collections.singletonList(key),
                    String.valueOf(now),
                    String.valueOf(windowMs),
                    String.valueOf(roomMaxRequests),
                    member);
            return result != null && result == 1L;
        } catch (Exception e) {
            log.error("Sliding window check failed: videoId={}", videoId);
            return true; // 降级放行
        }
    }

    private boolean checkLeakyBucket(String ip, long now) {
        try {
            String key = LEAKY_BUCKET_KEY + ip;
            Long result = redisTemplate.execute(
                    leakyBucketScript,
                    Collections.singletonList(key),
                    String.valueOf(now),
                    String.valueOf(ipCapacity),
                    String.valueOf(ipLeakRate));
            return result != null && result == 1L;
        } catch (Exception e) {
            log.error("Leaky bucket check failed: ip={}", ip);
            return true; // 降级放行
        }
    }
}
