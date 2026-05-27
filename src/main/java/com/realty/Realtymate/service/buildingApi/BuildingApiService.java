package com.realty.Realtymate.service.buildingApi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service
public class BuildingApiService {

    private static final String KAKAO_KEY    = "7cf9fabf536e86f4f6cacc450aa4bf5d";
    private static final String BUILDING_KEY = "GLa5XDpYhDb4giRYUDzyVI8C10pVtlmGNULVHLiOoE2YBtE2sqP0d5pwyu3fPmNFc5qXCwRC7Bst4pkil43bNg==";
    private static final String VWORLD_KEY   = "661D39B1-E058-305B-A503-AF5A77733891";

    private final WebClient kakaoClient;
    private final WebClient buildingClient;
    private final WebClient vworldClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public BuildingApiService() {
        this.kakaoClient    = WebClient.builder().baseUrl("https://dapi.kakao.com").build();
        this.buildingClient = WebClient.builder().baseUrl("https://apis.data.go.kr").build();
        this.vworldClient   = WebClient.builder().baseUrl("https://api.vworld.kr").build();
    }

    // ── 주소 검색 ─────────────────────────────────────────────────────────────

    public Mono<Map<String, Object>> geocodeAddress(String query) {
        return kakaoClient.get()
            .uri(u -> u.path("/v2/local/search/address.json")
                .queryParam("query", query).build())
            .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + KAKAO_KEY)
            .retrieve()
            .bodyToMono(String.class)
            .map(this::parseGeocodeResponse)
            .onErrorResume(e -> {
                log.error("지오코딩 실패: {}", e.getMessage());
                return Mono.just(Map.of("status", "error", "message", e.getMessage(), "candidates", List.of()));
            });
    }

    private Map<String, Object> parseGeocodeResponse(String resp) {
        try {
            JsonNode root = mapper.readTree(resp);
            List<Map<String, Object>> candidates = new ArrayList<>();
            for (JsonNode doc : root.path("documents")) {
                JsonNode addr = doc.path("address");
                String bCode  = addr.path("b_code").asText("");
                String mainNo = addr.path("main_address_no").asText("0");
                String subNo  = addr.path("sub_address_no").asText("0");
                boolean isMtn = "Y".equals(addr.path("mountain_yn").asText("N"));

                Map<String, Object> c = new LinkedHashMap<>();
                c.put("address_name", addr.path("address_name").asText());
                c.put("x",           doc.path("x").asText());
                c.put("y",           doc.path("y").asText());
                c.put("sigunguCd",   bCode.length() >= 5  ? bCode.substring(0, 5)  : "");
                c.put("bjdongCd",    bCode.length() >= 10 ? bCode.substring(5, 10) : "");
                c.put("bun",         padZero(mainNo));
                c.put("ji",          padZero(subNo));
                c.put("platGbCd",    isMtn ? "1" : "0");
                candidates.add(c);
            }
            return Map.of("status", "success", "candidates", candidates);
        } catch (Exception e) {
            throw new RuntimeException("지오코딩 파싱 실패: " + e.getMessage(), e);
        }
    }

    // ── 건물 분석 ─────────────────────────────────────────────────────────────

    public Mono<Map<String, Object>> analyzeBuilding(
            String sigunguCd, String bjdongCd, String platGbCd,
            String bun, String ji, String x, String y) {

        Mono<Object> titleMono  = callBuildingApi("/1613000/BldRgstHubService/getBrTitleInfo",   sigunguCd, bjdongCd, platGbCd, bun, ji)
            .cast(Object.class).onErrorReturn(Map.of());
        Mono<Object> basisMono  = callBuildingApi("/1613000/BldRgstHubService/getBrBasisOulnInfo", sigunguCd, bjdongCd, platGbCd, bun, ji)
            .cast(Object.class).onErrorReturn(Map.of());
        Mono<Object> floorsMono = callBuildingApi("/1613000/BldRgstHubService/getBrFlrOulnInfo",  sigunguCd, bjdongCd, platGbCd, bun, ji)
            .cast(Object.class).onErrorReturn(Map.of());

        Mono<String> districtMono = (x != null && !x.isBlank() && y != null && !y.isBlank())
            ? getDistrictPlan(x, y).onErrorReturn("")
            : Mono.just("");

        return Mono.zip(titleMono, basisMono, floorsMono, districtMono)
            .map(t -> {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("status",       "success");
                result.put("title",        t.getT1());
                result.put("basis",        t.getT2());
                result.put("floors",       t.getT3());
                result.put("districtPlan", t.getT4().isEmpty() ? null : t.getT4());
                return result;
            });
    }

    // ── 내부 헬퍼 ─────────────────────────────────────────────────────────────

    private Mono<Map<String, Object>> callBuildingApi(String path,
            String sigunguCd, String bjdongCd, String platGbCd,
            String bun, String ji) {
        try {
            String encodedKey = URLEncoder.encode(BUILDING_KEY, StandardCharsets.UTF_8);
            String url = "https://apis.data.go.kr" + path
                + "?serviceKey=" + encodedKey
                + "&sigunguCd="  + sigunguCd
                + "&bjdongCd="   + bjdongCd
                + "&platGbCd="   + platGbCd
                + "&bun="        + bun
                + "&ji="         + ji
                + "&_type=json&numOfRows=100&pageNo=1";

            return buildingClient.get()
                .uri(URI.create(url))
                .retrieve()
                .bodyToMono(String.class)
                .map(resp -> {
                    try { return (Map<String, Object>) mapper.readValue(resp, Map.class); }
                    catch (Exception e) { throw new RuntimeException(e); }
                })
                .doOnError(e -> log.warn("건축물대장 API 실패 [{}]: {}", path, e.getMessage()));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    private Mono<String> getDistrictPlan(String x, String y) {
        return vworldClient.get()
            .uri(u -> u.path("/req/data")
                .queryParam("service",    "data")
                .queryParam("request",    "getfeature")
                .queryParam("data",       "LT_C_UPISUQ161")
                .queryParam("key",        VWORLD_KEY)
                .queryParam("geometry",   "false")
                .queryParam("attribute",  "true")
                .queryParam("crs",        "EPSG:4326")
                .queryParam("format",     "json")
                .queryParam("geomfilter", "POINT(" + x + " " + y + ")")
                .queryParam("columns",    "uname")
                .build())
            .retrieve()
            .bodyToMono(String.class)
            .map(resp -> {
                try {
                    JsonNode root     = mapper.readTree(resp);
                    JsonNode features = root.path("response").path("result")
                        .path("featureCollection").path("features");
                    if (features.isArray() && !features.isEmpty()) {
                        return features.get(0).path("properties").path("uname").asText("");
                    }
                } catch (Exception ignored) {}
                return "";
            })
            .doOnError(e -> log.warn("지구단위계획 실패: {}", e.getMessage()));
    }

    private String padZero(String no) {
        try { return String.format("%04d", (int) Double.parseDouble(no.trim())); }
        catch (Exception e) { return "0000"; }
    }
}
