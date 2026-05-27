package com.realty.Realtymate.service.commercialApi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class GolmokApiService {

    private static final String API_BASE_URL = "https://golmok.seoul.go.kr";
    private static final int TIMEOUT_SECONDS = 10;

    @Value("${golmok.api.cookie:}")
    private String golmokCookie;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public GolmokApiService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder
            .baseUrl(API_BASE_URL)
            .build();
        this.objectMapper = objectMapper;
    }

    private WebClient.RequestBodySpec applyCommonHeaders(WebClient.RequestBodySpec spec) {
        return spec
            .header("Accept", "*/*")
            .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .header("Origin", "https://golmok.seoul.go.kr")
            .header("Referer", "https://golmok.seoul.go.kr/owner/owner.do")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("X-Requested-With", "XMLHttpRequest");
    }

    private MultiValueMap<String, String> createBaseParams(String wktPolygon, double area, String analysisRelm) {
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int quarter = 2;

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("name", "직접그리기");
        params.add("wkt", wktPolygon);
        params.add("area", String.valueOf(area));
        params.add("analysisRelm", analysisRelm);
        params.add("gbnCd", "WKT");
        params.add("relmName", "반경/다각형");
        params.add("svcIndutyCd", "CS000000");
        params.add("svcIndutyNm", "업종전체");
        params.add("stdrYyCd", String.valueOf(year));
        params.add("stdrQuCd", String.valueOf(quarter));

        return params;
    }

    private Mono<Map<String, Object>> callApi(String endpoint, MultiValueMap<String, String> params) {
        return applyCommonHeaders(webClient.post().uri(endpoint))
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData(params))
            .retrieve()
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .onErrorResume(e -> {
                log.error("골목상권 API 호출 실패: {}", endpoint, e);
                return Mono.just("{}");
            })
            .map(response -> {
                try {
                    if (response == null || response.trim().isEmpty()) {
                        return createErrorResponse("빈 응답");
                    }
                    JsonNode jsonNode = objectMapper.readTree(response);
                    return createSuccessResponse(jsonNode);
                } catch (Exception e) {
                    log.error("골목상권 API 처리 중 오류: {}", endpoint, e);
                    return createErrorResponse("API 호출 중 오류: " + e.getMessage());
                }
            });
    }

    public Mono<Map<String, Object>> getBlockArea(String wktPolygon) {
        // log.info("골목상권 블록 영역 조회 시작");

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("wkt", wktPolygon);

        return applyCommonHeaders(webClient.post().uri("/analysis/selectBlockArea.json"))
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData(params))
            .retrieve()
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .onErrorResume(e -> {
                log.error("블록 영역 조회 실패", e);
                return Mono.just("{}");
            })
            .map(response -> {
                try {
                    JsonNode jsonNode = objectMapper.readTree(response);
                    // log.info("블록 영역 조회 성공");
                    return createSuccessResponse(jsonNode);
                } catch (Exception e) {
                    log.error("블록 영역 조회 중 오류", e);
                    return createErrorResponse("블록 영역 조회 중 오류: " + e.getMessage());
                }
            });
    }

    public Mono<Map<String, Object>> getWrcPopltSexAge(String wktPolygon, double area, String analysisRelm) {
        // log.info("성별/연령별 직장인구 조회");
        return callApi("/analysis/selectAnalysisWrcPopltnSexAge.json", createBaseParams(wktPolygon, area, analysisRelm));
    }

    public Mono<Map<String, Object>> getWrcPopltHa(String wktPolygon, double area, String analysisRelm) {
        // log.info("직장인구 수/밀도 조회");
        return callApi("/analysis/selectAnalysisWrcPopltnHa.json", createBaseParams(wktPolygon, area, analysisRelm));
    }

    public Mono<Map<String, Object>> getRepopSexAge(String wktPolygon, double area, String analysisRelm) {
        // log.info("성별/연령별 거주인구 조회");
        return callApi("/analysis/selectAnalysisRepopSexAge.json", createBaseParams(wktPolygon, area, analysisRelm));
    }

    public Mono<Map<String, Object>> getSelngHour(String wktPolygon, double area, String analysisRelm) {
        // log.info("시간대별 매출 조회");
        return callApi("/analysis/selectAnalysisSelngHour.json", createBaseParams(wktPolygon, area, analysisRelm));
    }

    public Mono<Map<String, Object>> getSelngWeek(String wktPolygon, double area, String analysisRelm) {
        // log.info("요일별 매출 조회");
        return callApi("/analysis/selectAnalysisSelngWeek.json", createBaseParams(wktPolygon, area, analysisRelm));
    }

    public Mono<Map<String, Object>> getSelngAge(String wktPolygon, double area, String analysisRelm) {
        // log.info("연령대별 매출 조회");
        return callApi("/analysis/selectAnalysisSelngAge.json", createBaseParams(wktPolygon, area, analysisRelm));
    }

    public Mono<Map<String, Object>> getFlpopWeekCo(String wktPolygon, double area, String analysisRelm) {
        // log.info("요일별 유동인구 조회");
        return callApi("/analysis/selectAnalysisFlpopWeekCo.json", createBaseParams(wktPolygon, area, analysisRelm));
    }

    public Mono<Map<String, Object>> getFlpopHourCo(String wktPolygon, double area, String analysisRelm) {
        // log.info("시간대별 유동인구 조회");
        return callApi("/analysis/selectAnalysisFlpopHourCo.json", createBaseParams(wktPolygon, area, analysisRelm));
    }

    public Mono<Map<String, Object>> getAptHshldCo(String wktPolygon, double area, String analysisRelm) {
        // log.info("아파트 가구수 조회");
        return callApi("/analysis/selectAnalysisAptHshldCo.json", createBaseParams(wktPolygon, area, analysisRelm));
    }

    public Mono<Map<String, Object>> getRepopDnstCo(String wktPolygon, double area, String analysisRelm) {
        // log.info("거주인구 밀도 조회");
        return callApi("/analysis/selectAnalysisRepopDnstCo.json", createBaseParams(wktPolygon, area, analysisRelm));
    }

    public Mono<Map<String, Object>> getFlpopCo(String wktPolygon, double area, String analysisRelm) {
        // log.info("총 유동인구 조회");
        return callApi("/analysis/selectAnalysisFlpopCo.json", createBaseParams(wktPolygon, area, analysisRelm));
    }

    public Mono<Map<String, Object>> getFlpopSexAge(String wktPolygon, double area, String analysisRelm) {
        // log.info("성별/연령별 유동인구 조회");
        return callApi("/analysis/selectAnalysisFlpopSexAge.json", createBaseParams(wktPolygon, area, analysisRelm));
    }

    public Mono<Map<String, Object>> getSeoulGolmokAnalysis(String wktPolygon, double area) {
        // log.info("=== 서울 골목상권 API 통합 호출 시작 ===");

        return getBlockArea(wktPolygon)
            .flatMap(blockAreaResult -> {
                if (!"success".equals(blockAreaResult.get("status"))) {
                    return Mono.just(createErrorResponse("블록 영역 조회 실패"));
                }

                JsonNode blockData = (JsonNode) blockAreaResult.get("data");
                String analysisRelm = extractAnalysisRelm(blockData);

                if (analysisRelm == null || analysisRelm.isEmpty()) {
                    log.warn("analysisRelm 값을 찾을 수 없음, 기본값 사용");
                    analysisRelm = "500";
                }

                // log.info("추출된 analysisRelm: {}", analysisRelm);

                String finalAnalysisRelm = analysisRelm;

                // Mono.zip은 최대 8개까지만 지원하므로 두 그룹으로 나눔
                Mono<Map<String, Object>> group1 = Mono.zip(
                    getWrcPopltSexAge(wktPolygon, area, finalAnalysisRelm),
                    getWrcPopltHa(wktPolygon, area, finalAnalysisRelm),
                    getRepopSexAge(wktPolygon, area, finalAnalysisRelm),
                    getSelngHour(wktPolygon, area, finalAnalysisRelm),
                    getSelngWeek(wktPolygon, area, finalAnalysisRelm),
                    getSelngAge(wktPolygon, area, finalAnalysisRelm),
                    getFlpopWeekCo(wktPolygon, area, finalAnalysisRelm),
                    getFlpopHourCo(wktPolygon, area, finalAnalysisRelm)
                ).map(tuple -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("wrc_poplt_sex_age", tuple.getT1());
                    map.put("wrc_poplt_ha", tuple.getT2());
                    map.put("repop_sex_age", tuple.getT3());
                    map.put("selng_hour", tuple.getT4());
                    map.put("selng_week", tuple.getT5());
                    map.put("selng_age", tuple.getT6());
                    map.put("flpop_week_co", tuple.getT7());
                    map.put("flpop_hour_co", tuple.getT8());
                    return map;
                });

                Mono<Map<String, Object>> group2 = Mono.zip(
                    getAptHshldCo(wktPolygon, area, finalAnalysisRelm),
                    getRepopDnstCo(wktPolygon, area, finalAnalysisRelm),
                    getFlpopCo(wktPolygon, area, finalAnalysisRelm),
                    getFlpopSexAge(wktPolygon, area, finalAnalysisRelm)
                ).map(tuple -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("apt_hshld_co", tuple.getT1());
                    map.put("repop_dnst_co", tuple.getT2());
                    map.put("flpop_co", tuple.getT3());
                    map.put("flpop_sex_age", tuple.getT4());
                    return map;
                });

                // 두 그룹의 결과를 병합
                return Mono.zip(group1, group2).map(tuple -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("block_area", blockData);
                    result.putAll(tuple.getT1()); // group1 결과
                    result.putAll(tuple.getT2()); // group2 결과

                    // log.info("=== 서울 골목상권 API 통합 호출 완료 ===");
                    return createSuccessResponse(result);
                });
            })
            .onErrorResume(e -> {
                log.error("골목상권 통합 조회 중 오류", e);
                return Mono.just(createErrorResponse("통합 조회 중 오류: " + e.getMessage()));
            });
    }

    private String extractAnalysisRelm(JsonNode blockData) {
        if (blockData != null && blockData.has("analysisRelm")) {
            return blockData.get("analysisRelm").asText();
        }
        if (blockData != null && blockData.has("data")) {
            JsonNode data = blockData.get("data");
            if (data.isArray() && data.size() > 0) {
                JsonNode first = data.get(0);
                if (first.has("analysisRelm")) {
                    return first.get("analysisRelm").asText();
                }
            }
        }
        return "500";
    }

    private Map<String, Object> createSuccessResponse(Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        result.put("data", data);
        return result;
    }

    /**
     * 골목상권 유동인구 LineString 데이터 조회 (WKT 형식)
     * @param minx EPSG:5181 최소 X 좌표
     * @param miny EPSG:5181 최소 Y 좌표
     * @param maxx EPSG:5181 최대 X 좌표
     * @param maxy EPSG:5181 최대 Y 좌표
     * @return LineString 데이터 (WKT → 좌표 변환됨)
     */
    public Mono<Map<String, Object>> getFlowpopLineStrings(
        double minx, double miny, double maxx, double maxy
    ) {
        // log.info("골목상권 유동인구 LineString 조회");
        // log.info("범위: minx={}, miny={}, maxx={}, maxy={}", minx, miny, maxx, maxy);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("minx", String.valueOf(minx));
        params.add("miny", String.valueOf(miny));
        params.add("maxx", String.valueOf(maxx));
        params.add("maxy", String.valueOf(maxy));
        params.add("wkt", "");
        params.add("dayweek", "1");  // 요일 (1=월요일)
        params.add("agrde", "00");   // 연령대
        params.add("tmzon", "00");   // 시간대
        params.add("ext", "ext");
        params.add("signguCd", "11"); // 서울

        return applyCommonHeaders(webClient.post().uri("/tool/wfs/fpop.json"))
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData(params))
            .retrieve()
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .onErrorResume(e -> {
                log.error("골목상권 LineString 조회 실패", e);
                return Mono.just("[]");
            })
            .map(response -> {
                try {
                    JsonNode jsonNode = objectMapper.readTree(response);
                    if (!jsonNode.isArray()) {
                        return createErrorResponse("잘못된 응답 형식");
                    }

//                    log.info("골목상권 LineString 조회 성공: {}개", jsonNode.size());
                    return createSuccessResponse(jsonNode);
                } catch (Exception e) {
//                    log.error("골목상권 LineString 처리 중 오류", e);
                    return createErrorResponse("LineString 처리 중 오류: " + e.getMessage());
                }
            });
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "error");
        result.put("message", message);
        return result;
    }
}
