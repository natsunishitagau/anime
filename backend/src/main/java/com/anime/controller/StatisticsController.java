package com.anime.controller;

import com.anime.dto.ApiResponse;
import com.anime.service.StatisticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/stats")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOverview() {
        Map<String, Object> stats = statisticsService.getTodayStats();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/pv")
    public ResponseEntity<ApiResponse<Long>> getPV(@RequestParam(defaultValue = "today") String period) {
        long pv = statisticsService.getPV(period);
        return ResponseEntity.ok(ApiResponse.success(pv));
    }

    @GetMapping("/uv")
    public ResponseEntity<ApiResponse<Long>> getUV(@RequestParam(defaultValue = "today") String period) {
        long uv = statisticsService.getUV(period);
        return ResponseEntity.ok(ApiResponse.success(uv));
    }

    @PostMapping("/cleanup")
    public ResponseEntity<ApiResponse<Void>> cleanupOldData(@RequestParam(defaultValue = "30") int days) {
        statisticsService.cleanupOldData(days);
        return ResponseEntity.ok(ApiResponse.success("Cleanup completed", null));
    }
}
