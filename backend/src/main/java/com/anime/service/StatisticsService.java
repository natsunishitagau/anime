package com.anime.service;

import com.anime.entity.DailyStats;
import com.anime.repository.DailyStatsRepository;
import com.anime.util.RedisUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Set;

@Service
public class StatisticsService {
    @Autowired
    private RedisUtil redisUtil;
    
    @Autowired
    private DailyStatsRepository dailyStatsRepository;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private static final int SWITCH_TO_HLL_THRESHOLD = 100;

    public void recordVisit(String userId, String ip) {
        String today = LocalDate.now().format(DATE_FORMAT);
        String currentHour = LocalDateTime.now().format(HOUR_FORMAT);
        String thisMonth = LocalDate.now().format(MONTH_FORMAT);

        // PV统计
        redisUtil.incr("pv:total");
        redisUtil.incr("pv:daily:" + today);
        redisUtil.incr("pv:hourly:" + currentHour);

        // UV统计 - 智能切换策略
        String visitorId = userId != null ? userId : ip;
        if (visitorId != null && !visitorId.isEmpty()) {
            recordUV("daily", today, visitorId);
            recordUV("monthly", thisMonth, visitorId);
        }
    }

    private void recordUV(String period, String date, String visitorId) {
        String setKey = "uv:" + period + ":" + date + ":set";
        String hllKey = "uv:" + period + ":" + date + ":hll";
        String switchFlagKey = "uv:" + period + ":" + date + ":switched";

        boolean hasSwitched = redisUtil.get(switchFlagKey) != null;
        
        if (!hasSwitched) {
            Long setSize = redisUtil.sSize(setKey);
            
            if (setSize == null || setSize < SWITCH_TO_HLL_THRESHOLD) {
                redisUtil.sAdd(setKey, visitorId);
                setSize = redisUtil.sSize(setKey);
                
                if (setSize >= SWITCH_TO_HLL_THRESHOLD) {
                    switchToHyperLogLog(setKey, hllKey, switchFlagKey);
                }
            } else {
                switchToHyperLogLog(setKey, hllKey, switchFlagKey);
                redisUtil.pfAdd(hllKey, visitorId);
            }
        } else {
            redisUtil.pfAdd(hllKey, visitorId);
        }
    }

    private void switchToHyperLogLog(String setKey, String hllKey, String switchFlagKey) {
        Set<Object> members = redisUtil.sMembers(setKey);

        String[] memberArray = members.toArray(new String[0]);
        redisUtil.pfAdd(hllKey, memberArray);
        
        redisUtil.set(switchFlagKey, "true");
        redisUtil.delete(setKey);
    }

    public long getPV(String period) {
        String key = switch (period.toLowerCase()) {
            case "today" -> "pv:daily:" + LocalDate.now().format(DATE_FORMAT);
            case "hour" -> "pv:hourly:" + LocalDateTime.now().format(HOUR_FORMAT);
            case "month" -> "pv:daily:" + LocalDate.now().format(MONTH_FORMAT);
            default -> "pv:total";
        };
        Object result = redisUtil.get(key);
        return result != null ? ((Number) result).longValue() : 0;
    }

    public long getUV(String period) {
        String today = LocalDate.now().format(DATE_FORMAT);
        String thisMonth = LocalDate.now().format(MONTH_FORMAT);

        String uvPeriod = switch (period.toLowerCase()) {
            case "month" -> "monthly";
            default -> "daily";
        };

        String date = switch (period.toLowerCase()) {
            case "month" -> thisMonth;
            default -> today;
        };

        String setKey = "uv:" + uvPeriod + ":" + date + ":set";
        String hllKey = "uv:" + uvPeriod + ":" + date + ":hll";
        String switchFlagKey = "uv:" + uvPeriod + ":" + date + ":switched";

        boolean hasSwitched = redisUtil.get(switchFlagKey) != null;

        if (hasSwitched) {
            return redisUtil.pfCount(hllKey);
        } 

        Long count = redisUtil.sSize(setKey);
        return count != null ? count : 0;
    }

    public java.util.Map<String, Object> getTodayStats() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("pv", getPV("today"));
        stats.put("uv", getUV("today"));
        stats.put("hourlyPv", getPV("hour"));
        return stats;
    }

    public void cleanupOldData(int retentionDays) {
        LocalDate cutoffDate = LocalDate.now().minusDays(retentionDays);
        String cutoffStr = cutoffDate.format(DATE_FORMAT);

        cleanupPattern("pv:daily:*", cutoffStr, "pv:daily:");
        cleanupPattern("pv:hourly:*", cutoffStr.substring(0, 13), "pv:hourly:");
        cleanupUVPattern("uv:daily:", cutoffStr);
        cleanupUVPattern("uv:monthly:", cutoffDate.format(MONTH_FORMAT));
    }

    private void cleanupPattern(String pattern, String cutoff, String prefix) {
        try {
            java.util.Set<String> keys = redisUtil.keys(pattern);
            if (keys != null) {
                keys.stream()
                    .filter(k -> k.substring(prefix.length()).compareTo(cutoff) < 0)
                    .forEach(redisUtil::delete);
            }
        } catch (Exception ignored) {

        }
    }

    private void cleanupUVPattern(String prefix, String cutoff) {
        try {
            java.util.Set<String> setKeys = redisUtil.keys(prefix + "*:set");
            java.util.Set<String> hllKeys = redisUtil.keys(prefix + "*:hll");
            java.util.Set<String> flagKeys = redisUtil.keys(prefix + "*:switched");

            cleanupUVKeys(setKeys, prefix, cutoff);
            cleanupUVKeys(hllKeys, prefix, cutoff);
            cleanupUVKeys(flagKeys, prefix, cutoff);
        } catch (Exception ignored) {

        }
    }

    private void cleanupUVKeys(java.util.Set<String> keys, String prefix, String cutoff) {
        if (keys == null) return;
        keys.stream()
            .filter(k -> {
                String datePart = k.substring(prefix.length());
                int endIndex = datePart.indexOf(':');
                if (endIndex > 0) {
                    datePart = datePart.substring(0, endIndex);
                }
                return datePart.compareTo(cutoff) < 0;
            })
            .forEach(redisUtil::delete);
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void saveDailyStats() {
        try {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            String yesterdayStr = yesterday.format(DATE_FORMAT);
            
            long pv = getPVForDate(yesterdayStr);
            long uv = getUVForDate(yesterdayStr);
            
            Optional<DailyStats> existing = dailyStatsRepository.findByStatDate(yesterday);
            if (existing.isPresent()) {
                DailyStats stats = existing.get();
                stats.setPv(pv);
                stats.setUv(uv);
                dailyStatsRepository.save(stats);
            } else {
                DailyStats stats = new DailyStats(yesterday, pv, uv);
                dailyStatsRepository.save(stats);
            }
            
            cleanupOldData(30);
        } catch (Exception e) {
            System.err.println("Error saving daily stats: " + e.getMessage());
        }
    }

    private long getPVForDate(String date) {
        Object result = redisUtil.get("pv:daily:" + date);
        return result != null ? ((Number) result).longValue() : 0;
    }

    private long getUVForDate(String date) {
        String setKey = "uv:daily:" + date + ":set";
        String hllKey = "uv:daily:" + date + ":hll";
        String switchFlagKey = "uv:daily:" + date + ":switched";

        boolean hasSwitched = redisUtil.get(switchFlagKey) != null;

        if (hasSwitched) {
            return redisUtil.pfCount(hllKey);
        } 

        Long count = redisUtil.sSize(setKey);
        return count != null ? count : 0;
    }

    public Optional<DailyStats> getDailyStats(LocalDate date) {
        return dailyStatsRepository.findByStatDate(date);
    }
}
