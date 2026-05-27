package com.realty.Realtymate.controller;

import com.realty.Realtymate.service.buildingApi.BuildingApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/building")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class BuildingAnalysisController {

    private final BuildingApiService buildingApiService;

    /**
     * 주소 → 건축물대장 파라미터 변환
     * POST /api/building/geocode
     */
    @PostMapping("/geocode")
    public Mono<ResponseEntity<Map<String, Object>>> geocode(@RequestBody Map<String, String> body) {
        String address = body.getOrDefault("address", "").trim();
        if (address.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest()
                .body(Map.of("status", "error", "message", "주소를 입력하세요")));
        }
        log.info("건물분석 지오코딩: {}", address);
        return buildingApiService.geocodeAddress(address)
            .map(ResponseEntity::ok)
            .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", "지오코딩 중 오류 발생")));
    }

    /**
     * 건물 종합 분석 (표제부 + 기본개요 + 층별개요 + 지구단위계획)
     * POST /api/building/analyze
     */
    @PostMapping("/analyze")
    public Mono<ResponseEntity<Map<String, Object>>> analyze(@RequestBody Map<String, String> body) {
        String sigunguCd = body.getOrDefault("sigunguCd", "");
        String bjdongCd  = body.getOrDefault("bjdongCd",  "");
        String platGbCd  = body.getOrDefault("platGbCd",  "0");
        String bun       = body.getOrDefault("bun",       "");
        String ji        = body.getOrDefault("ji",        "");
        String x         = body.getOrDefault("x",         "");
        String y         = body.getOrDefault("y",         "");

        if (sigunguCd.isEmpty() || bjdongCd.isEmpty() || bun.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest()
                .body(Map.of("status", "error", "message", "필수 파라미터(sigunguCd, bjdongCd, bun) 누락")));
        }

        log.info("건물분석: {}{} bun={} ji={}", sigunguCd, bjdongCd, bun, ji);
        return buildingApiService.analyzeBuilding(sigunguCd, bjdongCd, platGbCd, bun, ji, x, y)
            .map(ResponseEntity::ok)
            .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", "건물 분석 중 오류 발생")));
    }
}
