package com.realty.Realtymate.service.commercialApi;

import com.realty.Realtymate.utils.GeoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 상권 분석 통합 서비스
 * - 나이스비즈맵, 골목상권, OpenAI API 통합
 * - 반경/폴리곤 기반 상권 분석
 */
@Slf4j
@Service
public class CommercialAnalysisService {

    private final NiceBizmapApiService niceBizmapApiService;
    private final GolmokApiService golmokApiService;
    private final OpenAIService openAIService;
    private final GeminiService geminiService;

    // 명시적 생성자 (IDE 경고 방지)
    public CommercialAnalysisService(
            NiceBizmapApiService niceBizmapApiService,
            GolmokApiService golmokApiService,
            OpenAIService openAIService,
            GeminiService geminiService) {
        this.niceBizmapApiService = niceBizmapApiService;
        this.golmokApiService = golmokApiService;
        this.openAIService = openAIService;
        this.geminiService = geminiService;
    }

    /**
     * 기본 분석 (반경 기반)
     * @param latitude 위도 (WGS84)
     * @param longitude 경도 (WGS84)
     * @param radius 반경 (미터)
     * @return 통합 분석 결과
     */
    public Mono<Map<String, Object>> analyzeBasic(double latitude, double longitude, int radius) {
        // log.info("=== 상권 분석 시작 (반경) ===");
        // log.info("위치: {}, {}", latitude, longitude);
        // log.info("반경: {}m", radius);

        try {
            // 1. WKT 폴리곤 생성 (EPSG:5181)
            String wktPolygon = GeoUtils.createCirclePolygonWkt(latitude, longitude, radius);
            double area = GeoUtils.calculateCircleArea(radius);

            // log.info("생성된 WKT: {}", wktPolygon.substring(0, Math.min(100, wktPolygon.length())) + "...");
            // log.info("면적: {}㎡", String.format("%.2f", area));

            // 2. 좌표 경계 계산 (EPSG:5181)
            double[] epsg5181Center = GeoUtils.transformWgs84ToEpsg5181(latitude, longitude);
            double minx = epsg5181Center[0] - radius;
            double miny = epsg5181Center[1] - radius;
            double maxx = epsg5181Center[0] + radius;
            double maxy = epsg5181Center[1] + radius;

            // 3. 나이스비즈맵, 골목상권 통계, 골목상권 LineString을 병렬로 호출
            Mono<Map<String, Object>> niceMono = niceBizmapApiService.getFloatingPopulationByRadius(
                latitude, longitude, radius);
            Mono<Map<String, Object>> golmokMono = golmokApiService.getSeoulGolmokAnalysis(wktPolygon, area);
            Mono<Map<String, Object>> golmokLinesMono = golmokApiService.getFlowpopLineStrings(minx, miny, maxx, maxy);

            // 4. 세 API 호출 결과를 결합
            return Mono.zip(niceMono, golmokMono, golmokLinesMono)
                .flatMap(tuple -> {
                    Map<String, Object> niceData = tuple.getT1();
                    Map<String, Object> golmokData = tuple.getT2();
                    Map<String, Object> golmokLinesData = tuple.getT3();

                    // 5. 골목상권 LineString WKT → 좌표 변환
                    Map<String, Object> golmokWithCoords = convertGolmokWktToCoords(golmokLinesData);

                    // 내부 데이터 객체 구성
                    Map<String, Object> data = new HashMap<>();
                    data.put("floating_population", niceData);
                    data.put("golmok_analysis", golmokData);
                    data.put("nice", niceData);  // 클라이언트 호환성
                    data.put("golmok", golmokWithCoords);  // LineString 좌표 포함

                    // 6. AI 분석 생성 (Gemini만 사용)
                    if ("success".equals(golmokData.get("status"))) {
                        Map<String, Object> golmokDataMap = (Map<String, Object>) golmokData.get("data");

                        // Gemini 분석
                        String aiSummary = geminiService.generateAISummary(golmokDataMap);
                        data.put("ai_summary", aiSummary);


                    } else {
                        data.put("ai_summary", "골목상권 데이터가 없어 AI 분석을 생성할 수 없습니다.");
                    }

                    data.put("metadata", Map.of(
                        "latitude", latitude,
                        "longitude", longitude,
                        "radius", radius,
                        "area", area,
                        "wkt", wktPolygon
                    ));

                    // 최종 응답 구조
                    Map<String, Object> result = new HashMap<>();
                    result.put("status", "success");
                    result.put("message", "상권 분석 완료");
                    result.put("data", data);

                    // log.info("=== 상권 분석 완료 ===");
                    return Mono.just(result);
                })
                .onErrorResume(e -> {
                    log.error("상권 분석 중 오류", e);
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("status", "error");
                    errorResult.put("message", "상권 분석 중 오류: " + e.getMessage());
                    return Mono.just(errorResult);
                });

        } catch (Exception e) {
            log.error("상권 분석 중 오류", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "error");
            errorResult.put("message", "상권 분석 중 오류: " + e.getMessage());
            return Mono.just(errorResult);
        }
    }

    /**
     * 폴리곤 기반 분석
     * @param polygonCoords [[lat1, lng1], [lat2, lng2], ...]
     * @return 통합 분석 결과
     */
    public Mono<Map<String, Object>> analyzePolygon(List<double[]> polygonCoords) {
        // log.info("=== 상권 분석 시작 (폴리곤) ===");
        // log.info("폴리곤 점 개수: {}", polygonCoords.size());

        try {
            // 1. WKT 폴리곤 생성 (EPSG:5181)
            String wktPolygon = GeoUtils.createPolygonWkt(polygonCoords);
            double area = GeoUtils.calculatePolygonArea(polygonCoords);

            // log.info("생성된 WKT: {}", wktPolygon.substring(0, Math.min(100, wktPolygon.length())) + "...");
            // log.info("면적: {}㎡", String.format("%.2f", area));

            // 2. 폴리곤 중심점 계산 (나이스 API용)
            double[] center = calculatePolygonCenter(polygonCoords);
            double centerLat = center[0];
            double centerLng = center[1];
            int approximateRadius = (int) Math.sqrt(area / Math.PI); // 근사 반경

            // 3. 폴리곤 경계 계산 (EPSG:5181)
            double minLat = polygonCoords.stream().mapToDouble(c -> c[0]).min().orElse(0);
            double maxLat = polygonCoords.stream().mapToDouble(c -> c[0]).max().orElse(0);
            double minLng = polygonCoords.stream().mapToDouble(c -> c[1]).min().orElse(0);
            double maxLng = polygonCoords.stream().mapToDouble(c -> c[1]).max().orElse(0);

            double[] minCoord5181 = GeoUtils.transformWgs84ToEpsg5181(minLat, minLng);
            double[] maxCoord5181 = GeoUtils.transformWgs84ToEpsg5181(maxLat, maxLng);

            double minx = minCoord5181[0];
            double miny = minCoord5181[1];
            double maxx = maxCoord5181[0];
            double maxy = maxCoord5181[1];

            // 4. 나이스비즈맵, 골목상권 통계, 골목상권 LineString을 병렬로 호출
            Mono<Map<String, Object>> niceMono = niceBizmapApiService.getFloatingPopulationByRadius(
                centerLat, centerLng, approximateRadius);
            Mono<Map<String, Object>> golmokMono = golmokApiService.getSeoulGolmokAnalysis(wktPolygon, area);
            Mono<Map<String, Object>> golmokLinesMono = golmokApiService.getFlowpopLineStrings(minx, miny, maxx, maxy);

            // 5. 세 API 호출 결과를 결합
            return Mono.zip(niceMono, golmokMono, golmokLinesMono)
                .flatMap(tuple -> {
                    Map<String, Object> niceData = tuple.getT1();
                    Map<String, Object> golmokData = tuple.getT2();
                    Map<String, Object> golmokLinesData = tuple.getT3();

                    // 6. 골목상권 LineString WKT → 좌표 변환
                    Map<String, Object> golmokWithCoords = convertGolmokWktToCoords(golmokLinesData);

                    // 내부 데이터 객체 구성
                    Map<String, Object> data = new HashMap<>();
                    data.put("floating_population", niceData);
                    data.put("golmok_analysis", golmokData);
                    data.put("nice", niceData);  // 클라이언트 호환성
                    data.put("golmok", golmokWithCoords);  // LineString 좌표 포함

                    // 7. AI 분석 생성 (Gemini만 사용)
                    if ("success".equals(golmokData.get("status"))) {
                        Map<String, Object> golmokDataMap = (Map<String, Object>) golmokData.get("data");

                        // Gemini 분석
                        String aiSummary = geminiService.generateAISummary(golmokDataMap);
                        data.put("ai_summary", aiSummary);


                    } else {
                        data.put("ai_summary", "골목상권 데이터가 없어 AI 분석을 생성할 수 없습니다.");
                    }

                    data.put("metadata", Map.of(
                        "polygon_points", polygonCoords.size(),
                        "area", area,
                        "wkt", wktPolygon,
                        "center_lat", centerLat,
                        "center_lng", centerLng
                    ));

                    // 최종 응답 구조
                    Map<String, Object> result = new HashMap<>();
                    result.put("status", "success");
                    result.put("message", "상권 분석 완료");
                    result.put("data", data);

                    // log.info("=== 상권 분석 완료 ===");
                    return Mono.just(result);
                })
                .onErrorResume(e -> {
                    log.error("상권 분석 중 오류", e);
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("status", "error");
                    errorResult.put("message", "상권 분석 중 오류: " + e.getMessage());
                    return Mono.just(errorResult);
                });

        } catch (Exception e) {
            log.error("상권 분석 중 오류", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "error");
            errorResult.put("message", "상권 분석 중 오류: " + e.getMessage());
            return Mono.just(errorResult);
        }
    }

    /**
     * 골목상권 데이터만 조회
     */
    public Mono<Map<String, Object>> getGolmokDataOnly(double latitude, double longitude, int radius) {
        try {
            String wktPolygon = GeoUtils.createCirclePolygonWkt(latitude, longitude, radius);
            double area = GeoUtils.calculateCircleArea(radius);
            return golmokApiService.getSeoulGolmokAnalysis(wktPolygon, area);
        } catch (Exception e) {
            log.error("골목상권 데이터 조회 중 오류", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "error");
            errorResult.put("message", "골목상권 데이터 조회 중 오류: " + e.getMessage());
            return Mono.just(errorResult);
        }
    }

    /**
     * 나이스 데이터만 조회
     */
    public Mono<Map<String, Object>> getNiceDataOnly(double latitude, double longitude, int radius) {
        try {
            return niceBizmapApiService.getFloatingPopulationByRadius(latitude, longitude, radius);
        } catch (Exception e) {
            log.error("나이스 데이터 조회 중 오류", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "error");
            errorResult.put("message", "나이스 데이터 조회 중 오류: " + e.getMessage());
            return Mono.just(errorResult);
        }
    }

    /**
     * AI 요약만 생성 (OpenAI)
     */
    public Mono<Map<String, Object>> getAISummaryOnly(Map<String, Object> golmokData) {
        try {
            String aiSummary = openAIService.generateAISummary(golmokData);
            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("summary", aiSummary);
            result.put("provider", "openai");
            return Mono.just(result);
        } catch (Exception e) {
            log.error("AI 요약 생성 중 오류", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "error");
            errorResult.put("message", "AI 요약 생성 중 오류: " + e.getMessage());
            return Mono.just(errorResult);
        }
    }

    /**
     * AI 요약만 생성 (Gemini)
     */
    public Mono<Map<String, Object>> getGeminiSummaryOnly(Map<String, Object> golmokData) {
        try {
            String aiSummary = geminiService.generateAISummary(golmokData);
            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("summary", aiSummary);
            result.put("provider", "gemini");
            return Mono.just(result);
        } catch (Exception e) {
            log.error("Gemini 요약 생성 중 오류", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "error");
            errorResult.put("message", "Gemini 요약 생성 중 오류: " + e.getMessage());
            return Mono.just(errorResult);
        }
    }

    /**
     * AI 요약 (OpenAI + Gemini 둘 다)
     */
    public Mono<Map<String, Object>> getBothAISummaries(Map<String, Object> golmokData) {
        try {
            // OpenAI와 Gemini를 병렬로 호출
            Mono<String> openaiMono = Mono.fromCallable(() -> openAIService.generateAISummary(golmokData));
            Mono<String> geminiMono = Mono.fromCallable(() -> geminiService.generateAISummary(golmokData));

            return Mono.zip(openaiMono, geminiMono)
                .map(tuple -> {
                    String openaiSummary = tuple.getT1();
                    String geminiSummary = tuple.getT2();

                    Map<String, Object> result = new HashMap<>();
                    result.put("status", "success");
                    result.put("openai_summary", openaiSummary);
                    result.put("gemini_summary", geminiSummary);


                    return result;
                })
                .onErrorResume(e -> {
                    log.error("AI 요약 생성 중 오류", e);
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("status", "error");
                    errorResult.put("message", "AI 요약 생성 중 오류: " + e.getMessage());
                    return Mono.just(errorResult);
                });

        } catch (Exception e) {
            log.error("AI 요약 생성 중 오류", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "error");
            errorResult.put("message", "AI 요약 생성 중 오류: " + e.getMessage());
            return Mono.just(errorResult);
        }
    }

    /**
     * 폴리곤 중심점 계산 (단순 평균)
     */
    private double[] calculatePolygonCenter(List<double[]> coords) {
        double sumLat = 0.0;
        double sumLng = 0.0;

        for (double[] coord : coords) {
            sumLat += coord[0];
            sumLng += coord[1];
        }

        return new double[]{sumLat / coords.size(), sumLng / coords.size()};
    }

    /**
     * 골목상권 LineString WKT → 좌표 배열 변환
     * @param golmokLinesData {status: "success", data: [{wkt: "LINESTRING(...)", grade: 5, ...}, ...]}
     * @return {status: "success", data: [{coords: [[lat, lng], ...], grade: 5, ...}, ...]}
     */
    private Map<String, Object> convertGolmokWktToCoords(Map<String, Object> golmokLinesData) {
        try {
            if (!"success".equals(golmokLinesData.get("status"))) {
                return golmokLinesData;
            }

            com.fasterxml.jackson.databind.JsonNode dataNode =
                (com.fasterxml.jackson.databind.JsonNode) golmokLinesData.get("data");

            if (dataNode == null || !dataNode.isArray()) {
                return golmokLinesData;
            }

            List<Map<String, Object>> convertedLines = new ArrayList<>();

            for (com.fasterxml.jackson.databind.JsonNode lineNode : dataNode) {
                if (!lineNode.has("wkt")) {
                    continue;
                }

                String wktString = lineNode.get("wkt").asText();
                int grade = lineNode.has("grade") ? lineNode.get("grade").asInt() : 3;
                String roadLinkId = lineNode.has("roadLinkId") ? lineNode.get("roadLinkId").asText() : "";

                // WKT 파싱: "LINESTRING(x1 y1, x2 y2, ...)"
                List<double[]> coords = parseWktLineString(wktString);

                if (coords.isEmpty()) {
                    continue;
                }

                Map<String, Object> lineData = new HashMap<>();
                lineData.put("coords", coords);
                lineData.put("grade", grade);
                lineData.put("roadLinkId", roadLinkId);

                convertedLines.add(lineData);
            }

            // log.info("골목상권 LineString 변환 완료: {}개", convertedLines.size());

            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("data", convertedLines);
            return result;

        } catch (Exception e) {
            log.error("골목상권 WKT 변환 중 오류", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "error");
            errorResult.put("message", "WKT 변환 중 오류: " + e.getMessage());
            return errorResult;
        }
    }

    /**
     * WKT LINESTRING 파싱 및 EPSG:5181 → WGS84 변환
     * @param wkt "LINESTRING(x1 y1, x2 y2, ...)"
     * @return [[lat1, lng1], [lat2, lng2], ...]
     */
    private List<double[]> parseWktLineString(String wkt) {
        List<double[]> coords = new ArrayList<>();

        try {
            // "LINESTRING (" 또는 "LINESTRING(" 제거, 마지막 ")" 제거
            String coordsStr = wkt.replace("LINESTRING (", "")
                                  .replace("LINESTRING(", "")
                                  .replace(")", "")
                                  .trim();

            // "x1 y1, x2 y2, ..." 형식 파싱
            String[] points = coordsStr.split(",");

            for (String point : points) {
                String[] xy = point.trim().split("\\s+");
                if (xy.length >= 2) {
                    double x = Double.parseDouble(xy[0]);
                    double y = Double.parseDouble(xy[1]);

                    // EPSG:5181 → WGS84 변환
                    double[] wgs84 = GeoUtils.transformEpsg5181ToWgs84(x, y);
                    double lat = wgs84[0];
                    double lng = wgs84[1];

                    coords.add(new double[]{lat, lng});
                }
            }

        } catch (Exception e) {
            log.error("WKT LineString 파싱 실패: {}", wkt, e);
        }

        return coords;
    }
}
