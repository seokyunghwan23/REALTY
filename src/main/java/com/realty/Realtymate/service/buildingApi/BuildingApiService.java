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

    private static final Set<String> ZONE_TYPES = Set.of(
        "제1종전용주거지역", "제2종전용주거지역",
        "제1종일반주거지역", "제2종일반주거지역", "제3종일반주거지역",
        "준주거지역",
        "중심상업지역", "일반상업지역", "근린상업지역", "유통상업지역",
        "전용공업지역", "일반공업지역", "준공업지역",
        "보전녹지지역", "생산녹지지역", "자연녹지지역"
    );

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

    // ── 건물 종합 분석 ────────────────────────────────────────────────────────

    public Mono<Map<String, Object>> analyzeBuilding(
            String sigunguCd, String bjdongCd, String platGbCd,
            String bun, String ji, String x, String y) {

        Mono<Object> titleMono  = callBuildingApi("/1613000/BldRgstHubService/getBrTitleInfo",    sigunguCd, bjdongCd, platGbCd, bun, ji)
            .cast(Object.class).onErrorReturn(Map.of());
        Mono<Object> basisMono  = callBuildingApi("/1613000/BldRgstHubService/getBrBasisOulnInfo", sigunguCd, bjdongCd, platGbCd, bun, ji)
            .cast(Object.class).onErrorReturn(Map.of());
        Mono<Object> floorsMono = callBuildingApi("/1613000/BldRgstHubService/getBrFlrOulnInfo",   sigunguCd, bjdongCd, platGbCd, bun, ji)
            .cast(Object.class).onErrorReturn(Map.of());
        Mono<String> districtMono = (x != null && !x.isBlank() && y != null && !y.isBlank())
            ? getDistrictPlan(x, y).onErrorReturn("")
            : Mono.just("");

        return Mono.zip(titleMono, basisMono, floorsMono, districtMono)
            .flatMap(t -> {
                Object titleData  = t.getT1();
                Object basisData  = t.getT2();
                Object floorsData = t.getT3();
                String district   = t.getT4();

                double platArea = extractPlatArea(titleData);
                String zone     = extractZoneFromBasis(basisData);

                // 대지면적이 0이면 V-World 토지임야 API로 fallback
                Mono<Double> landMono = (platArea <= 0)
                    ? getLandArea(sigunguCd, bjdongCd, platGbCd, bun, ji)
                    : Mono.just(0.0);

                // 기본개요에 용도지역이 없으면 지역지구구역 API로 fallback
                Mono<String> jijiguMono = zone.isEmpty()
                    ? getJijiguInfo(sigunguCd, bjdongCd, platGbCd, bun, ji)
                    : Mono.just("");

                return Mono.zip(landMono, jijiguMono).map(t2 -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("status",       "success");
                    result.put("title",        titleData);
                    result.put("basis",        basisData);
                    result.put("floors",       floorsData);
                    result.put("districtPlan", district.isEmpty() ? null : district);
                    if (t2.getT1() > 0)        result.put("landArea",   t2.getT1());
                    if (!t2.getT2().isEmpty()) result.put("jijiguZone", t2.getT2());
                    return result;
                });
            });
    }

    // ── 데이터 추출 헬퍼 ──────────────────────────────────────────────────────

    private double extractPlatArea(Object titleData) {
        try {
            JsonNode root  = mapper.valueToTree(titleData);
            JsonNode items = root.path("response").path("body").path("items").path("item");
            JsonNode first = items.isArray()
                ? (items.size() > 0 ? items.get(0) : mapper.nullNode())
                : items;
            return first.path("platArea").asDouble(0);
        } catch (Exception e) { return 0; }
    }

    private String extractZoneFromBasis(Object basisData) {
        try {
            JsonNode root  = mapper.valueToTree(basisData);
            JsonNode items = root.path("response").path("body").path("items").path("item");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    String z = item.path("jiyukCdNm").asText("").trim();
                    if (!z.isEmpty()) return z;
                }
            } else if (!items.isMissingNode() && !items.isNull()) {
                return items.path("jiyukCdNm").asText("").trim();
            }
        } catch (Exception ignored) {}
        return "";
    }

    // ── API 호출 ──────────────────────────────────────────────────────────────

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

    // 지역지구구역 조회 — 용도지역 fallback용 (기본개요에 없을 때)
    private Mono<String> getJijiguInfo(String sigunguCd, String bjdongCd, String platGbCd, String bun, String ji) {
        try {
            String encodedKey = URLEncoder.encode(BUILDING_KEY, StandardCharsets.UTF_8);
            String url = "https://apis.data.go.kr/1613000/BldRgstHubService/getBrJijiguInfo"
                + "?serviceKey=" + encodedKey
                + "&sigunguCd=" + sigunguCd + "&bjdongCd=" + bjdongCd
                + "&platGbCd=" + platGbCd + "&bun=" + bun + "&ji=" + ji
                + "&_type=json&numOfRows=100&pageNo=1";

            return buildingClient.get()
                .uri(URI.create(url))
                .retrieve()
                .bodyToMono(String.class)
                .map(resp -> {
                    try {
                        JsonNode root  = mapper.readTree(resp);
                        JsonNode items = root.path("response").path("body").path("items").path("item");
                        List<String> zoneNames = new ArrayList<>();
                        if (items.isArray()) {
                            for (JsonNode item : items) {
                                String name = item.path("jijiguCdNm").asText("").trim();
                                if (ZONE_TYPES.contains(name)) zoneNames.add(name);
                            }
                        } else if (!items.isMissingNode() && !items.isNull()) {
                            String name = items.path("jijiguCdNm").asText("").trim();
                            if (ZONE_TYPES.contains(name)) zoneNames.add(name);
                        }
                        String result = String.join(", ", zoneNames);
                        log.info("지역지구구역 용도지역: {}", result);
                        return result;
                    } catch (Exception e) { return ""; }
                })
                .doOnError(e -> log.warn("지역지구구역 API 실패: {}", e.getMessage()))
                .onErrorReturn("");
        } catch (Exception e) {
            return Mono.just("");
        }
    }

    // V-World 토지임야 API — 대지면적 fallback용 (platArea=0일 때)
    private Mono<Double> getLandArea(String sigunguCd, String bjdongCd, String platGbCd, String bun, String ji) {
        String vworldPlat = "1".equals(platGbCd) ? "2" : "1";
        String bunPadded  = String.format("%4s", bun).replace(' ', '0');
        String jiPadded   = String.format("%4s", ji).replace(' ', '0');
        String pnu        = sigunguCd + bjdongCd + vworldPlat + bunPadded + jiPadded;
        log.info("V-World 토지임야 PNU: {}", pnu);

        return vworldClient.get()
            .uri(u -> u.path("/ned/data/ladfrlList")
                .queryParam("pnu",       pnu)
                .queryParam("key",       VWORLD_KEY)
                .queryParam("format",    "json")
                .queryParam("numOfRows", "10")
                .queryParam("pageNo",    "1")
                .queryParam("domain",    "localhost")
                .build())
            .retrieve()
            .bodyToMono(String.class)
            .map(resp -> {
                try {
                    JsonNode root  = mapper.readTree(resp);
                    JsonNode items = root.path("ladfrlVOList").path("ladfrlVOList");
                    if (!items.isArray() || items.isEmpty()) {
                        items = root.path("response").path("body").path("items").path("item");
                    }
                    if (items.isArray() && items.size() > 0) {
                        String area = items.get(0).path("lndpclAr").asText("");
                        if (!area.isEmpty()) {
                            double v = Double.parseDouble(area);
                            log.info("V-World 토지면적: {}㎡", v);
                            return v;
                        }
                    }
                } catch (Exception e) { log.warn("토지임야 파싱 실패: {}", e.getMessage()); }
                return 0.0;
            })
            .doOnError(e -> log.warn("V-World 토지임야 API 실패: {}", e.getMessage()))
            .onErrorReturn(0.0);
    }

    private Mono<String> getDistrictPlan(String x, String y) {
        return vworldClient.get()
            .uri(u -> u.path("/req/data")
                .queryParam("service",    "data")
                .queryParam("request",    "GetFeature")
                .queryParam("data",       "LT_C_UPISUQ161")
                .queryParam("key",        VWORLD_KEY)
                .queryParam("geomFilter", "POINT(" + x + " " + y + ")")
                .queryParam("format",     "json")
                .queryParam("size",       "100")
                .build())
            .retrieve()
            .bodyToMono(String.class)
            .map(resp -> {
                try {
                    JsonNode root     = mapper.readTree(resp);
                    String status     = root.path("response").path("status").asText("");
                    JsonNode features = root.path("response").path("result")
                        .path("featureCollection").path("features");
                    if ("OK".equals(status) && features.isArray()) {
                        List<String> names = new ArrayList<>();
                        for (JsonNode f : features) {
                            JsonNode props = f.path("properties");
                            String nm = props.path("dgm_nm").asText(
                                        props.path("uname").asText("")).trim();
                            if (!nm.isEmpty()) names.add(nm);
                        }
                        return String.join(", ", names);
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
