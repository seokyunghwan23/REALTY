package com.realty.Realtymate.service.commercialApi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 나이스비즈맵 API 서비스
 * - 유동인구 데이터 조회
 */
@Slf4j
@Service
public class NiceBizmapApiService {

    private static final String API_BASE_URL = "https://m.nicebizmap.co.kr";
    private static final int TIMEOUT_SECONDS = 10;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public NiceBizmapApiService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder
            .baseUrl(API_BASE_URL)
            .build();
        this.objectMapper = objectMapper;
    }

    /**
     * 반경 기반 유동인구 데이터 조회 (비동기)
     * @param latitude 위도 (WGS84)
     * @param longitude 경도 (WGS84)
     * @param radius 반경 (미터)
     * @return API 응답 데이터 Mono
     */
    public Mono<Map<String, Object>> getFloatingPopulationByRadius(double latitude, double longitude, int radius) {
//        log.info("나이스비즈맵 API 호출 시작 - lat: {}, lng: {}, radius: {}m", latitude, longitude, radius);

        return webClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/api/explorer/flowpop/by-location")
                .queryParam("lat", String.valueOf(latitude))
                .queryParam("lng", String.valueOf(longitude))
                .queryParam("radius", String.valueOf(radius))
                .build())
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("Connection", "keep-alive")
            .header("Referer", "https://m.nicebizmap.co.kr/explorer/flowpop")
            .header("Sec-Fetch-Dest", "empty")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Site", "same-origin")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("sec-ch-ua", "\"Chromium\";v=\"142\", \"Google Chrome\";v=\"142\", \"Not_A Brand\";v=\"99\"")
            .header("sec-ch-ua-mobile", "?0")
            .header("sec-ch-ua-platform", "\"Windows\"")
            .retrieve()
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .onErrorResume(e -> {
                log.error("나이스비즈맵 API 호출 실패", e);
                return Mono.just("{}");
            })
            .map(response -> {
                try {
                    JsonNode jsonNode = objectMapper.readTree(response);

                    // Nice API grade 변환 (원본: 1=많음, 5=적음 → 변환: 5=많음, 1=적음)
                    // 골목 API와 동일한 등급 체계로 맞추기 위해 6 - grade로 반전
                    int convertedCount = 0;

                    // 응답 구조: {data: {data: {flowpopData: [...]}}} 또는 {data: {flowpopData: [...]}}
                    JsonNode flowpopData = null;
                    if (jsonNode.has("data")) {
                        JsonNode dataNode = jsonNode.get("data");

                        // data.data.flowpopData 경로 시도
                        if (dataNode.has("data") && dataNode.get("data").has("flowpopData")) {
                            flowpopData = dataNode.get("data").get("flowpopData");
//                            log.info("Nice API 경로: data.data.flowpopData");
                        }
                        // data.flowpopData 경로 시도
                        else if (dataNode.has("flowpopData")) {
                            flowpopData = dataNode.get("flowpopData");
//                            log.info("Nice API 경로: data.flowpopData");
                        }
                    }

                    if (flowpopData != null && flowpopData.isArray()) {
                        for (JsonNode point : flowpopData) {
                            if (point.has("grade") && point instanceof com.fasterxml.jackson.databind.node.ObjectNode) {
                                int originalGrade = point.get("grade").asInt();
                                int invertedGrade = 6 - originalGrade;  // 1→5, 2→4, 3→3, 4→2, 5→1
                                ((com.fasterxml.jackson.databind.node.ObjectNode) point).put("grade", invertedGrade);
                                convertedCount++;

                                // 첫 5개만 로그 출력
                                if (convertedCount <= 5) {
//                                    log.info("Grade 변환: {} → {}", originalGrade, invertedGrade);
                                }
                            }
                        }
                    }

                    Map<String, Object> result = new HashMap<>();
                    result.put("status", "success");
                    result.put("data", jsonNode);
//                    log.info("나이스비즈맵 API 호출 성공 (grade 변환 완료: {}개)", convertedCount);
                    return result;
                } catch (Exception e) {
                    log.error("나이스비즈맵 API 처리 중 오류", e);
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("status", "error");
                    errorResult.put("message", "API 호출 중 오류: " + e.getMessage());
                    return errorResult;
                }
            });
    }

    /**
     * API 응답에서 주요 데이터 추출
     * @param apiResponse API 원본 응답
     * @return 요약 데이터
     */
    public Map<String, Object> extractSummaryData(Map<String, Object> apiResponse) {
        Map<String, Object> summary = new HashMap<>();

        if ("success".equals(apiResponse.get("status"))) {
            JsonNode data = (JsonNode) apiResponse.get("data");

            if (data != null && !data.isNull()) {
                // 필요한 데이터 추출 (실제 응답 구조에 따라 조정 필요)
                summary.put("totalPopulation", data.has("totalPopulation") ? data.get("totalPopulation").asInt() : 0);
                summary.put("malePopulation", data.has("malePopulation") ? data.get("malePopulation").asInt() : 0);
                summary.put("femalePopulation", data.has("femalePopulation") ? data.get("femalePopulation").asInt() : 0);
                summary.put("age10s", data.has("age10s") ? data.get("age10s").asInt() : 0);
                summary.put("age20s", data.has("age20s") ? data.get("age20s").asInt() : 0);
                summary.put("age30s", data.has("age30s") ? data.get("age30s").asInt() : 0);
                summary.put("age40s", data.has("age40s") ? data.get("age40s").asInt() : 0);
                summary.put("age50s", data.has("age50s") ? data.get("age50s").asInt() : 0);
                summary.put("age60plus", data.has("age60plus") ? data.get("age60plus").asInt() : 0);
            }
        }

        return summary;
    }
}
