package com.realty.Realtymate.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.realty.Realtymate.model.ApartItemEntity;
import com.realty.Realtymate.model.OwnerDetailDto;
import com.realty.Realtymate.model.SanggaItemDto;
import com.realty.Realtymate.model.SanggaItemEntity;
import com.realty.Realtymate.service.RealtySerevice.RealtyWebService;
import com.realty.Realtymate.service.RepositoryService.ItemInfoService;
import com.realty.Realtymate.service.PropertyRecommendService;
import com.realty.Realtymate.service.naverApi.NaverApiService;
import com.realty.Realtymate.service.GoogleSheetApi.GoogleSheetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/items")
@CrossOrigin(origins = "*", allowedHeaders = "*")  // 모든 도메인 허용
public class ItemInfoController {

    @Autowired
    private ItemInfoService itemInfoService;

    @Autowired
    private RealtyWebService realtyWebService;

    @Autowired
    private PropertyRecommendService propertyRecommendService;

    @Autowired
    private NaverApiService naverApiService;

    @Autowired
    private GoogleSheetService googleSheetService;

    @GetMapping("/sangga")
    public ResponseEntity<List<SanggaItemEntity>> getSanggaItems() {
        List<SanggaItemEntity> items = itemInfoService.getItems();
        return ResponseEntity.ok(items);
    }

    @GetMapping("/apart")
    public ResponseEntity<List<ApartItemEntity>> getApartItems() {
        List<ApartItemEntity> items = itemInfoService.getApartItems();
        return ResponseEntity.ok(items);
    }

    /**
     * Google Sheets에서 모든 매물 데이터를 조회합니다.
     */
    @GetMapping("/properties")
    public ResponseEntity<Map<String, Object>> getAllProperties(
            @RequestParam(required = false) String sheetId,
            @RequestParam(required = false) String sheetName
    ) {
        try {
            List<Map<String, Object>> properties;
            // sheetId가 제공되면 해당 시트에서 조회
            if (sheetId != null && !sheetId.trim().isEmpty()) {
                System.out.println("사용자 시트 조회 요청");
                properties = googleSheetService.getAllProperties(sheetId, sheetName);
            } else {
                // 없으면 기본 시트 조회
                System.out.println("기본 시트 조회 요청");
                properties = googleSheetService.getAllProperties();
            }

            Map<String, Object> response = new HashMap<>();
            response.put("message", "조회 완료");
            response.put("count", properties.size());
            response.put("items", properties);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.err.println("매물 목록 조회 실패: " + e.getMessage());
            return ResponseEntity.badRequest().body(
                    Map.of("error", "매물 목록 조회 실패: " + e.getMessage())
            );
        }
    }

    @PostMapping("/address-search")
    public Mono<ResponseEntity<Map<String, String>>> searchAddressByUrl(@RequestBody Map<String, String> request) {
        String url = request.get("url");

        return realtyWebService.getAddressByUrl(url)
                .map(address -> {
                    Map<String, String> response = new HashMap<>();
                    response.put("address", address);
                    response.put("message", "주소 조회 완료");
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(e -> {
                    System.err.println("컨트롤러 에러: " + e.getMessage());
                    Map<String, String> response = new HashMap<>();
                    response.put("address", "");
                    response.put("message", "주소 조회 실패: " + e.getMessage());
                    return Mono.just(ResponseEntity.badRequest().body(response));
                });
    }

    @PostMapping("/search_ad")
    public Mono<ResponseEntity<Map<String, Object>>> searchItems(@RequestBody Map<String, Object> request) {

        // 요청 파라미터 추출
        String address = (String) request.get("address");
        String floor = (String) request.get("floor");
        String propertyType = (String) request.get("propertyType");
        Map<String, Boolean> platforms = (Map<String, Boolean>) request.get("platforms");
        Map<String, Boolean> transactions = (Map<String, Boolean>) request.get("transactions");

        // RealtyWebService를 통해 매물 검색 수행 (Mono 반환)
        return realtyWebService.searchAd(address, floor, propertyType, platforms, transactions)
                .map(results -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("message", "검색 완료");
                    response.put("count", results.size());
                    response.put("items", results);
                    return ResponseEntity.ok(response);
                });
    }

    /**
     * 매물번호 또는 URL로 소유자 상세 정보 조회 및 저장
     * - 매물번호만 입력: "2516844705"
     * - fin URL: "https://fin.land.naver.com/.../2516844705"
     * - articleNo URL: "https://...?articleNo=2516844705"
     */
    @PostMapping("/detail")
    public Mono<ResponseEntity<Map<String, Object>>> getItemDetailByUrl(@RequestBody Map<String, String> request) {

        String url = request.get("url");
        String employeeName = request.get("employeeName");
        // 내 매물 조회 여부
        boolean myProperties = "true".equals(request.get("myProperties"));
        // Authorization 토큰 (내 매물 조회 시 필요)
        String authorization = request.get("authorization");

        return realtyWebService.getOwnerDetailByUrl(url, employeeName, myProperties, authorization)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    String errorMsg = e.getMessage();
                    // NOT_MY_PROPERTY는 로그 없이 처리
                    if (errorMsg == null || !errorMsg.contains("NOT_MY_PROPERTY")) {
                        System.err.println("URL 조회 실패: " + errorMsg);
                    }
                    return Mono.just(
                            ResponseEntity.badRequest().body(
                                    Map.of("error", errorMsg != null ? errorMsg : "알 수 없는 오류")
                            )
                    );
                });
    }

    @PostMapping("/recommend")
    public Mono<ResponseEntity<Map<String, Object>>> getPropertyRecommend(@RequestBody Map<String, String> request) {
        String propertyId = request.get("propertyId");
        String mode = request.getOrDefault("mode", "recommend"); // 기본값: recommend

        if (propertyId == null || propertyId.trim().isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().body(
                    Map.of("error", "매물번호가 필요합니다.")
            ));
        }

        return propertyRecommendService.getRecommendedProperties(propertyId, mode)
                .map(result -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("message", "조회 완료");
                    response.put("data", result);
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(e -> {
                    System.err.println("매물 조회 실패: " + e.getMessage());
                    return Mono.just(ResponseEntity.badRequest().body(
                            Map.of("error", "매물 조회 실패: " + e.getMessage())
                    ));
                });
    }

    @PostMapping("/geocode")
    public Mono<ResponseEntity<Map<String, Object>>> getCoordinates(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> addresses = (List<String>) request.get("addresses");

        if (addresses == null || addresses.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().body(
                    Map.of("error", "주소 목록이 필요합니다.")
            ));
        }

        return Mono.fromCallable(() -> {
            System.out.println("========================================");
            System.out.println("좌표 변환 요청 - 주소 수: " + addresses.size());

            List<Map<String, Object>> coordinates = new ArrayList<>();
            for (int i = 0; i < addresses.size(); i++) {
                String address = addresses.get(i);
                System.out.println((i + 1) + ". " + address);

                Map<String, Object> coordInfo = new HashMap<>();
                coordInfo.put("address", address);
                coordInfo.put("index", i + 1);
                JsonNode geocodeResult = naverApiService.getGeocode(address);
                JsonNode addressesNode = geocodeResult.get("addresses");

                if (addressesNode != null && addressesNode.isArray() && addressesNode.size() > 0) {
                    JsonNode firstAddress = addressesNode.get(0);
                    double x = firstAddress.get("x").asDouble(); // 경도
                    double y = firstAddress.get("y").asDouble(); // 위도
                    coordInfo.put("lng", x);
                    coordInfo.put("lat", y);
                    coordInfo.put("success", true);
                    System.out.println("  -> 좌표: (" + x + ", " + y + ")");
                } else {
                    coordInfo.put("lng", null);
                    coordInfo.put("lat", null);
                    coordInfo.put("success", false);
                    System.out.println("  -> 좌표 없음");
                }
                coordinates.add(coordInfo);
            }

            System.out.println("========================================");

            Map<String, Object> response = new HashMap<>();
            response.put("message", "좌표 변환 완료");
            response.put("coordinates", coordinates);
            response.put("totalCount", addresses.size());
            response.put("successCount", coordinates.stream().filter(c -> (Boolean) c.get("success")).count());

            return ResponseEntity.ok(response);
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    @PostMapping("/static-map")
    public Mono<ResponseEntity<Map<String, Object>>> getStaticMap(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> addresses = (List<String>) request.get("addresses");

        if (addresses == null || addresses.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().body(
                    Map.of("error", "주소 목록이 필요합니다.")
            ));
        }

        return Mono.fromCallable(() -> {
            System.out.println("========================================");
            System.out.println("Static Map 생성 요청 - 주소 수: " + addresses.size());

            // 1. 주소를 좌표로 변환
            List<double[]> coordinates = new ArrayList<>();
            for (int i = 0; i < addresses.size(); i++) {
                String address = addresses.get(i);
                System.out.println((i + 1) + ". " + address);

                JsonNode geocodeResult = naverApiService.getGeocode(address);
                JsonNode addressesNode = geocodeResult.get("addresses");

                if (addressesNode != null && addressesNode.isArray() && addressesNode.size() > 0) {
                    JsonNode firstAddress = addressesNode.get(0);
                    double lng = firstAddress.get("x").asDouble(); // 경도
                    double lat = firstAddress.get("y").asDouble(); // 위도
                    coordinates.add(new double[]{lng, lat});
                    System.out.println("  -> 좌표: (" + lng + ", " + lat + ")");
                } else {
                    System.out.println("  -> 좌표 없음 (건너뜀)");
                }
            }

            if (coordinates.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "유효한 좌표가 없습니다.");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            // 2. Static Map 이미지 생성
            byte[] imageBytes = naverApiService.getStaticMapImage(coordinates);

            // 3. Base64 인코딩
            String base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes);

            System.out.println("Static Map 이미지 생성 완료");
            System.out.println("========================================");

            Map<String, Object> response = new HashMap<>();
            response.put("message", "지도 생성 완료");
            response.put("image", base64Image);
            response.put("coordinateCount", coordinates.size());

            return ResponseEntity.ok(response);
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }
}
