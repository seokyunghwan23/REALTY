package com.realty.Realtymate.controller;

import com.realty.Realtymate.service.commercialApi.CommercialAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 상권 분석 REST API 컨트롤러
 * - 기본 분석 (반경)
 * - 폴리곤 분석
 * - 골목상권/나이스 데이터 개별 조회
 * - AI 요약 생성
 */
@Slf4j
@RestController
@RequestMapping("/api/commercial")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class CommercialAnalysisController {

    private final CommercialAnalysisService commercialAnalysisService;

    /**
     * 헬스 체크
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "message", "상권 분석 API 정상 작동 중"
        ));
    }

    /**
     * 기본 분석 (반경)
     * POST /api/commercial/analyze
     *
     * Request Body:
     * {
     *   "latitude": 37.5665,
     *   "longitude": 126.9780,
     *   "radius": 500
     * }
     */
    @PostMapping("/analyze")
    public Mono<ResponseEntity<Map<String, Object>>> analyzeBasic(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        try {
            double latitude = Double.parseDouble(request.get("latitude").toString());
            double longitude = Double.parseDouble(request.get("longitude").toString());
            int radius = Integer.parseInt(request.getOrDefault("radius", 500).toString());

            log.info("[{}] 상권분석 - ({}, {})",
                employeeId != null ? employeeId : "Unknown",
                String.format("%.4f", latitude),
                String.format("%.4f", longitude));

            return commercialAnalysisService.analyzeBasic(latitude, longitude, radius)
                .map(ResponseEntity::ok);

        } catch (Exception e) {
            log.error("상권 분석 요청 처리 중 오류", e);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "status", "error",
                    "message", "상권 분석 중 오류: " + e.getMessage()
                )));
        }
    }

    /**
     * 폴리곤 분석
     * POST /api/commercial/analyze-polygon
     *
     * Request Body:
     * {
     *   "polygonCoords": [
     *     [37.5665, 126.9780],
     *     [37.5675, 126.9790],
     *     [37.5685, 126.9770]
     *   ]
     * }
     */
    @PostMapping("/analyze-polygon")
    public Mono<ResponseEntity<Map<String, Object>>> analyzePolygon(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        try {
            // 클라이언트는 "polygon" 또는 "polygonCoords" 필드로 보냄
            List<List<Double>> rawCoords = (List<List<Double>>) request.get("polygon");
            if (rawCoords == null) {
                rawCoords = (List<List<Double>>) request.get("polygonCoords");
            }

            // List<List<Double>> → List<double[]> 변환
            List<double[]> polygonCoords = rawCoords.stream()
                .map(coord -> new double[]{coord.get(0), coord.get(1)})
                .toList();

            // 폴리곤 중심 좌표 계산
            double centerLat = polygonCoords.stream().mapToDouble(c -> c[0]).average().orElse(0.0);
            double centerLng = polygonCoords.stream().mapToDouble(c -> c[1]).average().orElse(0.0);

            log.info("[{}] 폴리곤분석 - ({}, {})",
                employeeId != null ? employeeId : "Unknown",
                String.format("%.4f", centerLat),
                String.format("%.4f", centerLng));

            return commercialAnalysisService.analyzePolygon(polygonCoords)
                .map(ResponseEntity::ok);

        } catch (Exception e) {
            log.error("폴리곤 분석 요청 처리 중 오류", e);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "status", "error",
                    "message", "폴리곤 분석 중 오류: " + e.getMessage()
                )));
        }
    }

    /**
     * 골목상권 데이터만 조회
     * POST /api/commercial/golmok
     *
     * Request Body:
     * {
     *   "latitude": 37.5665,
     *   "longitude": 126.9780,
     *   "radius": 500
     * }
     */
    @PostMapping("/golmok")
    public Mono<ResponseEntity<Map<String, Object>>> getGolmokData(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        try {
            double latitude = Double.parseDouble(request.get("latitude").toString());
            double longitude = Double.parseDouble(request.get("longitude").toString());
            int radius = Integer.parseInt(request.getOrDefault("radius", 500).toString());

            log.info("[{}] 골목데이터 - ({}, {})",
                employeeId != null ? employeeId : "Unknown",
                String.format("%.4f", latitude),
                String.format("%.4f", longitude));

            return commercialAnalysisService.getGolmokDataOnly(latitude, longitude, radius)
                .map(ResponseEntity::ok);

        } catch (Exception e) {
            log.error("골목상권 데이터 조회 중 오류", e);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "status", "error",
                    "message", "골목상권 데이터 조회 중 오류: " + e.getMessage()
                )));
        }
    }

    /**
     * 나이스비즈맵 데이터만 조회
     * POST /api/commercial/nice
     *
     * Request Body:
     * {
     *   "latitude": 37.5665,
     *   "longitude": 126.9780,
     *   "radius": 500
     * }
     */
    @PostMapping("/nice")
    public Mono<ResponseEntity<Map<String, Object>>> getNiceData(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        try {
            double latitude = Double.parseDouble(request.get("latitude").toString());
            double longitude = Double.parseDouble(request.get("longitude").toString());
            int radius = Integer.parseInt(request.getOrDefault("radius", 500).toString());

            log.info("[{}] 나이스데이터 - ({}, {})",
                employeeId != null ? employeeId : "Unknown",
                String.format("%.4f", latitude),
                String.format("%.4f", longitude));

            return commercialAnalysisService.getNiceDataOnly(latitude, longitude, radius)
                .map(ResponseEntity::ok);

        } catch (Exception e) {
            log.error("나이스비즈맵 데이터 조회 중 오류", e);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "status", "error",
                    "message", "나이스비즈맵 데이터 조회 중 오류: " + e.getMessage()
                )));
        }
    }

    /**
     * AI 요약만 생성 (OpenAI)
     * POST /api/commercial/ai-summary
     *
     * Request Body:
     * {
     *   "golmok_analysis": { ... }
     * }
     */
    @PostMapping("/ai-summary")
    public Mono<ResponseEntity<Map<String, Object>>> getAISummary(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        try {
            Map<String, Object> golmokData = (Map<String, Object>) request.get("golmok_analysis");

            if (golmokData == null || golmokData.isEmpty()) {
                return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of(
                        "status", "error",
                        "message", "골목상권 데이터가 필요합니다."
                    )));
            }

            log.info("[{}] AI요약생성(OpenAI)", employeeId != null ? employeeId : "Unknown");

            return commercialAnalysisService.getAISummaryOnly(golmokData)
                .map(ResponseEntity::ok);

        } catch (Exception e) {
            log.error("AI 요약 생성 중 오류", e);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "status", "error",
                    "message", "AI 요약 생성 중 오류: " + e.getMessage()
                )));
        }
    }

    /**
     * AI 요약만 생성 (Gemini)
     * POST /api/commercial/gemini-summary
     *
     * Request Body:
     * {
     *   "golmok_analysis": { ... }
     * }
     */
    @PostMapping("/gemini-summary")
    public Mono<ResponseEntity<Map<String, Object>>> getGeminiSummary(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        try {
            Map<String, Object> golmokData = (Map<String, Object>) request.get("golmok_analysis");

            if (golmokData == null || golmokData.isEmpty()) {
                return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of(
                        "status", "error",
                        "message", "골목상권 데이터가 필요합니다."
                    )));
            }

            log.info("[{}] AI요약생성(Gemini)", employeeId != null ? employeeId : "Unknown");

            return commercialAnalysisService.getGeminiSummaryOnly(golmokData)
                .map(ResponseEntity::ok);

        } catch (Exception e) {
            log.error("Gemini 요약 생성 중 오류", e);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "status", "error",
                    "message", "Gemini 요약 생성 중 오류: " + e.getMessage()
                )));
        }
    }

    /**
     * AI 요약 (OpenAI + Gemini 둘 다)
     * POST /api/commercial/both-ai-summary
     *
     * Request Body:
     * {
     *   "golmok_analysis": { ... }
     * }
     */
    @PostMapping("/both-ai-summary")
    public Mono<ResponseEntity<Map<String, Object>>> getBothAISummary(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        try {
            Map<String, Object> golmokData = (Map<String, Object>) request.get("golmok_analysis");

            if (golmokData == null || golmokData.isEmpty()) {
                return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of(
                        "status", "error",
                        "message", "골목상권 데이터가 필요합니다."
                    )));
            }

            log.info("[{}] AI요약생성(OpenAI+Gemini)", employeeId != null ? employeeId : "Unknown");

            return commercialAnalysisService.getBothAISummaries(golmokData)
                .map(ResponseEntity::ok);

        } catch (Exception e) {
            log.error("AI 요약 생성 중 오류", e);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "status", "error",
                    "message", "AI 요약 생성 중 오류: " + e.getMessage()
                )));
        }
    }

    /**
     * 통합 분석 (나이스 + 골목 + AI)
     * POST /api/commercial/integrated
     *
     * Request Body:
     * {
     *   "latitude": 37.5665,
     *   "longitude": 126.9780,
     *   "radius": 500,
     *   "analysisType": "radius" | "polygon",
     *   "polygonCoords": [...] (optional, for polygon type)
     * }
     */
    @PostMapping("/integrated")
    public Mono<ResponseEntity<Map<String, Object>>> integratedAnalysis(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-Employee-Id", required = false) String employeeId) {
        try {
            String analysisType = request.getOrDefault("analysisType", "radius").toString();

            if ("polygon".equals(analysisType)) {
                List<List<Double>> rawCoords = (List<List<Double>>) request.get("polygonCoords");
                List<double[]> polygonCoords = rawCoords.stream()
                    .map(coord -> new double[]{coord.get(0), coord.get(1)})
                    .toList();

                double centerLat = polygonCoords.stream().mapToDouble(c -> c[0]).average().orElse(0.0);
                double centerLng = polygonCoords.stream().mapToDouble(c -> c[1]).average().orElse(0.0);

                log.info("[{}] 통합분석(폴리곤) - ({}, {})",
                    employeeId != null ? employeeId : "Unknown",
                    String.format("%.4f", centerLat),
                    String.format("%.4f", centerLng));
                return commercialAnalysisService.analyzePolygon(polygonCoords)
                    .map(ResponseEntity::ok);

            } else {
                double latitude = Double.parseDouble(request.get("latitude").toString());
                double longitude = Double.parseDouble(request.get("longitude").toString());
                int radius = Integer.parseInt(request.getOrDefault("radius", 500).toString());

                log.info("[{}] 통합분석(반경) - ({}, {})",
                    employeeId != null ? employeeId : "Unknown",
                    String.format("%.4f", latitude),
                    String.format("%.4f", longitude));
                return commercialAnalysisService.analyzeBasic(latitude, longitude, radius)
                    .map(ResponseEntity::ok);
            }

        } catch (Exception e) {
            log.error("통합 분석 요청 처리 중 오류", e);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "status", "error",
                    "message", "통합 분석 중 오류: " + e.getMessage()
                )));
        }
    }
}
