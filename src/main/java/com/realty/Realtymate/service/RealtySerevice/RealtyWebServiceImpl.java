package com.realty.Realtymate.service.RealtySerevice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper; // ObjectMapper 임포트 추가
import com.realty.Realtymate.service.GoogleSheetApi.GoogleSheetService; // GoogleSheetService 임포트 추가
import com.realty.Realtymate.model.OwnerDetailDto;
import com.realty.Realtymate.model.SanggaItemDto;
import com.realty.Realtymate.service.OwnerService.OwnerDetailService;
import com.realty.Realtymate.service.naverApi.NaverApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class RealtyWebServiceImpl implements RealtyWebService {

    @Autowired
    private NaverApiService naverApiService;

    @Autowired
    private OwnerDetailService ownerDetailService;

    // --- Google Sheets 관련 주입 추가 ---
    @Autowired
    private GoogleSheetService googleSheetService;

    @Autowired
    private ObjectMapper objectMapper;
    // ------------------------------------

    @Override
    public Mono<String> getAddressByUrl(String url) {
        return Mono.fromCallable(() -> {
                    // ... (기존 getAddressByUrl 구현 내용은 변경 없음)
                    try {
                        // URL 또는 매물번호에서 articleNo 파싱
                        String articleNo;

                        // 1. 숫자만 입력된 경우 (매물번호 직접 입력)
                        if (url.trim().matches("\\d+")) {
                            articleNo = url.trim();
                        }
                        // 2. fin이 포함된 URL
                        else if (url.contains("fin")) {
                            String[] parts = url.split("/");
                            articleNo = parts[parts.length - 1];
                        }
                        // 3. articleNo= 파라미터가 있는 URL
                        else if (url.contains("articleNo=")) {
                            String[] parts = url.split("articleNo=");
                            if (parts.length > 1) {
                                String articlePart = parts[1];
                                // & 또는 공백으로 끝나는 경우 처리
                                int endIndex = articlePart.indexOf("&");
                                if (endIndex > 0) {
                                    articlePart = articlePart.substring(0, endIndex);
                                }
                                articleNo = articlePart.length() > 10 ? articlePart.substring(0, 10) : articlePart;
                            } else {
                                throw new IllegalArgumentException("URL에서 articleNo를 찾을 수 없습니다");
                            }
                        }
                        else {
                            throw new IllegalArgumentException("올바른 URL 또는 매물번호를 입력하세요");
                        }


                        // NaverApiService를 통해 매물 정보 조회
                        SanggaItemDto item = naverApiService.getItem(articleNo);
                        // 주소 추출
                        String address = item.getAddress();
                        return address;

                    } catch (Exception e) {
                        System.err.println("주소 조회 실패: " + e.getMessage());
                        throw new RuntimeException("주소 조회 실패: " + e.getMessage(), e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<List<SanggaItemDto>> searchAd(
            String address,
            String floor,
            String propertyType,
            Map<String, Boolean> platforms,
            Map<String, Boolean> transactions
    ) {
        // ... (기존 searchAd 구현 내용은 변경 없음)
        return Mono.<List<SanggaItemDto>>fromCallable(() -> {
                    List<SanggaItemDto> allResults = new ArrayList<>();

                    try {
                        // Step 1 - 주소를 좌표로 변환 (Geocoding using Naver Map API)
                        JsonNode naverMapRes = naverApiService.getGeocode(address);
                        JsonNode addresses = naverMapRes.get("addresses");

                        if (addresses == null || addresses.isEmpty()) {
                            throw new IllegalArgumentException("주소를 확인해주세요.");
                        }

                        JsonNode firstAddress = addresses.get(0);
                        String roadAddress = firstAddress.get("roadAddress").asText();
                        JsonNode addressElements = firstAddress.get("addressElements");

                        // addressElements에서 지역 정보 추출
                        String sigungu = addressElements.get(1).get("longName").asText();
                        String dongmyun = addressElements.get(2).get("longName").asText();
                        String ri = addressElements.get(3).get("longName").asText();
                        String bonbu = addressElements.get(7).get("longName").asText();

                        // 빈 문자열이 아닌 값들만 남겨서 주소 조합
                        String inputNaverAddress = Stream.of(sigungu, dongmyun, ri, bonbu)
                                .filter(part -> part != null && !part.isEmpty())
                                .collect(Collectors.joining(" "));

                        double x = firstAddress.get("x").asDouble();
                        double y = firstAddress.get("y").asDouble();

                        // 검색 범위 계산 (±0.0003)
                        double minLat = y - 0.0003;
                        double minLng = x - 0.0003;
                        double maxLat = y + 0.0003;
                        double maxLng = x + 0.0003;

                        // String searchConditions = buildSearchConditions(transactions);

                        // Step 3 - 각 플랫폼별 검색 수행
                        if (platforms.getOrDefault("네이버", false)) {
                            ArrayList<String> naverArticleNos = naverApiService.getItemListByCoord(minLat, minLng, maxLat, maxLng, propertyType);

                            // 비동기로 매물 정보 조회 및 필터링
                            List<SanggaItemDto> naverResults = Flux.fromIterable(naverArticleNos)
                                    .parallel() // 병렬 처리
                                    .runOn(Schedulers.boundedElastic()) // 별도 스레드에서 실행
                                    .flatMap(articleNo -> Mono.fromCallable(() -> {
                                        try {
                                            // 매물 상세 정보 조회
                                            SanggaItemDto item = naverApiService.getItem(articleNo);

                                            // 좌표로 주소 변환
                                            if (item.getLatitude() != null && item.getLongitude() != null) {
                                                String itemAddress = naverApiService.getAddressByLatLng(
                                                        item.getLatitude(),
                                                        item.getLongitude()
                                                );

                                                // 입력 주소와 비교
                                                if (inputNaverAddress.equals(itemAddress)) {

                                                    // 층 필터링 (층 입력이 있는 경우)
                                                    if (floor != null && !floor.isEmpty()) {
                                                        try {
                                                            int targetFloor = Integer.parseInt(floor);
                                                            if (item.getFloor() != null && item.getFloor() == targetFloor) {
                                                                return item;
                                                            }
                                                        } catch (NumberFormatException e) {
                                                            // 층 파싱 실패 시 무시
                                                        }
                                                    } else {
                                                        // 층 필터링 없으면 바로 반환
                                                        return item;
                                                    }
                                                }
                                            }
                                            return null;
                                        } catch (Exception e) {
                                            System.err.println("매물 조회 실패: " + articleNo + " - " + e.getMessage());
                                            return null;
                                        }
                                    }))
                                    .sequential() // 다시 순차적으로
                                    .filter(item -> item != null) // null 제거
                                    .collectList() // List로 변환
                                    .block(); // 결과 대기

                            allResults.addAll(naverResults);
                        }

                        if (platforms.getOrDefault("네모", false)) {
                            // List<SanggaItemDto> nemoResults = searchNemo(minLat, minLng, maxLat, maxLng, inputNaverAddress, floor, transactions);
                            // allResults.addAll(nemoResults);
                        }

                        if (platforms.getOrDefault("직방", false)) {
                            // List<SanggaItemDto> zigbangResults = searchZigbang(y, x, inputNaverAddress, floor, transactions);
                            // allResults.addAll(zigbangResults);
                        }

                        if (platforms.getOrDefault("다방", false)) {
                            // List<SanggaItemDto> dabangResults = searchDabang(y, x, inputNaverAddress, floor, transactions);
                            // allResults.addAll(dabangResults);
                        }

                        if (platforms.getOrDefault("피터팬", false)) {
                            // List<SanggaItemDto> peterpanResults = searchPeterpan(y, x, inputNaverAddress, floor, transactions);
                            // allResults.addAll(peterpanResults);
                        }
                        return allResults;

                    } catch (Exception e) {
                        System.err.println("매물 검색 중 오류 발생: " + e.getMessage());
                        e.printStackTrace();
                        return new ArrayList<>();
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<OwnerDetailDto> getOwnerDetail(
            String url,
            String platform,
            String verificationTypeName,
            String verificationTypeCode,
            String establishRegistrationNo,
            String address
    ) {
        return ownerDetailService.getOwnerDetail(
                url,
                platform,
                verificationTypeName,
                verificationTypeCode,
                establishRegistrationNo,
                address,
                false,  // myProperties
                null,   // authorization
                0,      // photoCount (TODO: SanggaItemDto에서 가져오기)
                null    // realtorId (TODO: SanggaItemDto에서 가져오기)
        );
    }

    /**
     * URL/매물번호로 소유자 상세 정보 조회
     * (NaverApiService로 먼저 매물 정보 조회 후 getOwnerDetail 호출)
     */
    @Override
    public Mono<Map<String, Object>> getOwnerDetailByUrl(String url, String employeeName, boolean myProperties, String authorization) {
        return Mono.fromCallable(() -> {
                    try {
                        // URL 또는 매물번호에서 articleNo 파싱 (이전 로직과 동일)
                        String articleNo;

                        // 1. 숫자만 입력된 경우 (매물번호 직접 입력)
                        if (url.trim().matches("\\d+")) {
                            articleNo = url.trim();
                        }
                        // 2. fin이 포함된 URL
                        else if (url.contains("fin")) {
                            String[] parts = url.split("/");
                            articleNo = parts[parts.length - 1];
                        }
                        // 3. articleNo= 파라미터가 있는 URL
                        else if (url.contains("articleNo=")) {
                            String[] parts = url.split("articleNo=");
                            if (parts.length > 1) {
                                String articlePart = parts[1];
                                int endIndex = articlePart.indexOf("&");
                                if (endIndex > 0) {
                                    articlePart = articlePart.substring(0, endIndex);
                                }
                                articleNo = articlePart.length() > 10 ? articlePart.substring(0, 10) : articlePart;
                            } else {
                                throw new IllegalArgumentException("URL에서 articleNo를 찾을 수 없습니다");
                            }
                        }
                        else {
                            throw new IllegalArgumentException("올바른 URL 또는 매물번호를 입력하세요");
                        }

                        // NaverApiService를 통해 매물 정보 조회
                        SanggaItemDto item = naverApiService.getItem(articleNo);

                        // 필요한 정보 추출
                        String cpPcArticleUrl = Objects.requireNonNullElse(item.getCpPcArticleUrl(), "");
                        String platform = item.getCpName() != null
                                ? item.getCpName() + "/" + item.getPlatform()
                                : item.getPlatform();
                        String verificationTypeName = Objects.requireNonNullElse(item.getVerificationTypeName(), "");
                        String verificationTypeCode = Objects.requireNonNullElse(item.getVerificationTypeCode(), "");
                        String establishRegistrationNo = Objects.requireNonNullElse(item.getEstablishRegistrationNo(), "");
                        String address = Objects.requireNonNullElse(item.getAddress(), "");

                        // getOwnerDetail 호출 (myProperties, authorization, photoCount, realtorId 추가)
                        OwnerDetailDto ownerDetail = ownerDetailService.getOwnerDetail(
                                cpPcArticleUrl,
                                platform,
                                verificationTypeName,
                                verificationTypeCode,
                                establishRegistrationNo,
                                address,
                                myProperties,
                                authorization,
                                item.getPhotoCount() != null ? item.getPhotoCount() : 0,  // photoCount
                                item.getRealtorId()  // realtorId
                        ).block();

                        // 1. OwnerDetailDto를 Map<String, Object>으로 변환합니다.
                        @SuppressWarnings("unchecked")
                        Map<String, Object> resultData = objectMapper.convertValue(ownerDetail, Map.class);

                        // OwnerDetailDto에 없는 추가적인 정보 (verificationTypeName)를 Map에 추가합니다.
                        resultData.put("verfMthdNm", verificationTypeName); // 확인방식

                        // ✨ 파이썬처럼 문자열 타임스탬프를 추가하는 부분
                        LocalDateTime now = LocalDateTime.now();
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                        String timestampString = now.format(formatter);
                        resultData.put("timestamp", timestampString);

                        // ✨ 직원 이름 정보를 Map에 추가합니다.
                        resultData.put("employeeName", employeeName);

                        // 2. Google Sheets에 저장합니다.
                        googleSheetService.saveToGoogleSheet(resultData);

                        // 3. 연락처 정보가 있을 때만 연락처 시트에 저장
                        String contact = (String) resultData.get("contact");
                        if (contact != null && !contact.isEmpty()) {
                            googleSheetService.saveToContactSheet(resultData);
                        }

                        // 4. 저장된 Map 형태의 데이터를 최종적으로 반환합니다.
                        return resultData;

                    } catch (Exception e) {
                        // NOT_MY_PROPERTY는 로그 없이 전파
                        if (e.getMessage() != null && e.getMessage().contains("NOT_MY_PROPERTY")) {
                            throw new RuntimeException("NOT_MY_PROPERTY", e);
                        }
                        System.err.println("소유자 정보 조회 및 Google Sheet 저장 실패: " + e.getMessage());
                        throw new RuntimeException("소유자 정보 조회 및 Google Sheet 저장 실패: " + e.getMessage(), e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

}