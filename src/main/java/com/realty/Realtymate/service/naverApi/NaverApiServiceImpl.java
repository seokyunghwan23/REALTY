package com.realty.Realtymate.service.naverApi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realty.Realtymate.model.*;
import com.realty.Realtymate.repository.*;
import com.realty.Realtymate.service.GoogleSheetApi.GoogleSheetService;
import com.realty.Realtymate.service.RepositoryService.ItemInfoService;
import com.realty.Realtymate.service.RepositoryService.SanggaServieItemService;
import com.realty.Realtymate.service.telegramApi.TelegramApiService;
import com.realty.Realtymate.utils.KakaoService;
import com.realty.Realtymate.utils.PolygonUtils;
import com.realty.Realtymate.utils.Utils;
import org.locationtech.jts.geom.Polygon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.math.BigDecimal;

@Service
public class NaverApiServiceImpl implements NaverApiService {

    private final WebClient webClient;
    private final WebClient naverMapWebClient;
    private String naverToken;

    private HttpHeaders initHeaders;

    // Naver Map API 키 (준혁)
    private static final String NAVER_MAP_CLIENT_ID = "o4jd97ho9o";
    private static final String NAVER_MAP_CLIENT_SECRET = "hueWFZbY5Ej8P4CJq3G0DZ4Oyl2dKVET7A03xIYI";

    private final IamGroundItemRepository iamGroundItemRepository;
    private final IamgroundExceptRepository iamgroundExceptRepository;
    private final HostelItemRepository hostelItemRepository;
    private final HostelExceptRepository hostelExceptRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    KakaoService kakaoService = new KakaoService(WebClient.builder());

    @Autowired
    TelegramApiService telegramApiService;
    @Autowired
    ItemInfoService itemInfoService;
    @Autowired
    SanggaServieItemService sanggaServieItemService;
    @Autowired
    GoogleSheetService googleSheetService;

    public NaverApiServiceImpl(WebClient.Builder webClientBuilder, DongPnuRepository dongPnuRepository, IamGroundItemRepository iamGroundItemRepository, IamgroundExceptRepository iamgroundExceptRepository, HostelItemRepository hostelItemRepository, HostelExceptRepository hostelExceptRepository) {
        this.webClient = webClientBuilder.baseUrl("https://new.land.naver.com")
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB로 증가
                        .build())
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()))
                .build();

        // Naver Map API용 WebClient 생성
        this.naverMapWebClient = WebClient.builder()
                .baseUrl("https://maps.apigw.ntruss.com")
                .build();

        this.iamGroundItemRepository = iamGroundItemRepository;
        this.iamgroundExceptRepository = iamgroundExceptRepository;
        this.hostelItemRepository = hostelItemRepository;
        this.hostelExceptRepository = hostelExceptRepository;
        this.initHeaders = makeInitHeaders();
    }


    @Override
    public void alertHostelNewAd(ArrayList<String> previousList, List<String> dongPnu) {
        ArrayList<String> currentList = new ArrayList<>();
        dongPnu.forEach(pnu -> {
            currentList.addAll(getItemListByPnuForHostel(pnu));
        });

        ArrayList<String> newAd = Utils.checkNewAd(previousList, currentList);
        ArrayList<String> exceptList = hostelExceptRepository.findAllAddress();
        newAd.forEach(articleNo -> {
            hostelItemRepository.saveItemId(articleNo);
            SanggaItemDto item = getItem(articleNo);
            String address = item.getAddress();
            if (exceptList.contains(address)) {
                return;
            }
            String text = makeIamgroundNaverText(item);
            telegramApiService.sendMessage("-1002317091819", text);
        });
    }

    @Override
    public void alertIamGroundNewAd(ArrayList<String> previousList, List<String> dongPnu) {
        ArrayList<String> currentList = new ArrayList<>();
        dongPnu.forEach(pnu -> {
            currentList.addAll(getItemListByPnu(pnu));
        });
        ArrayList<String> newAd = Utils.checkNewAd(previousList, currentList);
        ArrayList<String> exceptList = iamgroundExceptRepository.findAllAddress();
        newAd.forEach(articleNo -> {
            iamGroundItemRepository.saveItemId(articleNo);

            SanggaItemDto item = getItem(articleNo);
            String address = item.getAddress();

//            if (!item.getTitle().contains("옥상") && !item.getDescription().contains("옥상")) {
//                if (exceptList.contains(address)) {
//                    return;
//                }
//            }
            if (!Optional.ofNullable(item.getTitle()).orElse("").contains("옥상") &&
                    !Optional.ofNullable(item.getDescription()).orElse("").contains("옥상")) {
                if (exceptList.contains(address)) {
                    return;
                }
            }


            String text = makeIamgroundNaverText(item);
            telegramApiService.sendMessage("-1001669505704", text);
        });

    }

    @Override
    public void alertSanggaNewAd(List<SanggaItemEntity> previousItemList, AgentCustomer agentCustomer) {
        //이전매물 받아오기
        ArrayList<String> previousList = (ArrayList<String>) previousItemList.stream()
                .filter(previousItem -> previousItem.getPlatform().equals("네이버"))
                .map(SanggaItemEntity::getItemId)
                .collect(Collectors.toList());

        ArrayList<String> currentList = getItemList(agentCustomer);
        ArrayList<String> newAd = Utils.checkNewAd(previousList, currentList);

        newAd.forEach(articleNo -> {
            SanggaItemDto sanggaItemDto = getItem(articleNo);  // 📌 매물 정보 가져오기

            itemInfoService.saveSanggaItem(sanggaItemDto);  // 원본 데이터 저장

            if (agentCustomer.getAgentName().equals("청운")) {
                if (!sanggaServieItemService.saveOrUpdateSanggaServeItem(sanggaItemDto)) {
                    String text = makeNaverText(sanggaItemDto);
                    telegramApiService.sendMessage(agentCustomer, text);
                }
            }
//            else if (agentCustomer.getAgentName().equals("베스트")) {
//                String text = makeNaverText(sanggaItemDto);
//                telegramApiService.sendMessage(agentCustomer, text);
//            }
        });

    }

    @Override
    public void alertOwnerAd(List<SanggaItemEntity> previousItemList, AgentCustomer agentCustomer) {
        ArrayList<String> currentList = getItemList(agentCustomer);
        ArrayList<String> previousList = previousItemList.stream()
                .map(SanggaItemEntity::getItemId)
                .collect(Collectors.toCollection(ArrayList::new));

        ArrayList<String> newAd = Utils.checkNewAd(previousList, currentList);

        newAd.forEach(articleNo -> {
            SanggaItemDto sanggaItemDto = getItem(articleNo);  // 📌 매물 정보 가져오기

            itemInfoService.saveSanggaItem(sanggaItemDto);  // 원본 데이터 저장

            if (agentCustomer.getAgentName().equals("청운")) {
                if (!sanggaServieItemService.saveOrUpdateSanggaServeItem(sanggaItemDto)) {
                    String articleTypeCode = sanggaItemDto.getArticleTypeCode();
                    boolean shouldSendAlert = false;

                    if ("C03".equals(articleTypeCode) || "D01".equals(articleTypeCode) || "D02".equals(articleTypeCode)) {
                        // C03(상가), D01(단독/다가구), D02(전원주택): 연락처 시트에서 주소 확인
                        String address = sanggaItemDto.getAddress();
                        boolean hasContact = googleSheetService.hasContactByAddress(address);
                        // 연락처가 없으면 알람 O (주소 없거나 연락처 없음)
                        shouldSendAlert = !hasContact;
                    } else if ("A06".equals(articleTypeCode)) {
                        // A06: 부동산포스가 아니면 알람 O
                        String cpName = sanggaItemDto.getCpName();
                        shouldSendAlert = !"부동산포스".equals(cpName);
                    }
                    // 그 외 articleTypeCode: 알람 X (shouldSendAlert = false 유지)

                    if (shouldSendAlert) {
                        String text = makeNaverText(sanggaItemDto);
                        telegramApiService.sendMessage(agentCustomer, text);
                    }
                }
            }
//            else if (agentCustomer.getAgentName().equals("베스트")) {
//                String text = makeNaverText(sanggaItemDto);
//                telegramApiService.sendMessage(agentCustomer, text);
//            }
        });

    }

    @Override
    public void alertApartNewAd(List<ApartItemEntity> previousItemList, ApartCustomer apartCustomer) {
        ArrayList<String> currentList = new ArrayList<>();
        apartCustomer.getComplexList().forEach(complexNo -> {
            currentList.addAll(getApartItemList(complexNo));
        });

        ArrayList<String> previousList = new ArrayList<>();
        previousItemList.forEach(item -> previousList.add(item.getItemId()));

        ArrayList<String> newAd = Utils.checkNewAd(previousList, currentList);
        newAd.forEach(articleNo -> {
            ApartItemDto apartItemDto = getApartItem(articleNo);  // 📌 매물 정보 가져오기
            itemInfoService.saveApartItem(apartItemDto);  // 원본 데이터 저장

            if (apartCustomer.getAgentName().equals("홍일")) {
                String text = makeNaverText(apartItemDto);
                telegramApiService.sendMessage(apartCustomer.getChatId(), text);
            }
        });
    }

    private String makeNaverText(ApartItemDto apartItemDto) {
        double monthlyManagementCost = apartItemDto.getManagementFee().doubleValue();

        // 보증금, 월세 (null 방지)
        String warrantPrice = Optional.ofNullable(apartItemDto.getDeposit())
                .map(BigDecimal::toPlainString)
                .orElse("0");
        String rentPrice = Optional.ofNullable(apartItemDto.getMonthlyFee())
                .map(BigDecimal::toPlainString)
                .orElse("0");
        String dealPrice = Optional.ofNullable(apartItemDto.getDealPrice())
                .map(p -> p.divide(BigDecimal.valueOf(10000), 2, RoundingMode.HALF_UP))
                .map(BigDecimal::toPlainString)
                .orElse("0");
        // 층수, 중개사 이름
        String floor = Optional.ofNullable(apartItemDto.getFloor())
                .map(f -> f + "층")
                .orElse("");

        String realtorName = Optional.ofNullable(apartItemDto.getAgentName())
                .orElse("");

        // Kakao API를 통해 주소 변환
        String kakaoAddress = Optional.ofNullable(apartItemDto.getAddress()).orElse("");
        String articleNo = Optional.ofNullable(apartItemDto.getItemId()).orElse("");
        String realestateTypeName = "아파트";
        String tradeTypeName = Optional.ofNullable(apartItemDto.getTradeType()).orElse("");
        String apartName = Optional.ofNullable(apartItemDto.getApartName()).orElse("");
        String dong = Optional.ofNullable(apartItemDto.getDong()).orElse("");
        String ho = apartItemDto.getHo();

        // ho가 비어있지 않으면 floor와 ho를 같이 표시
        String floorAndHo = (ho != null && !ho.trim().isEmpty()) ? floor + " " + ho + "호" : floor;


        if (tradeTypeName.equals("월세") || tradeTypeName.equals("전세")) {
            // 최종 텍스트 생성 (String.format() 사용)
            return String.format(
                    "네이버 %s %s\n%s\n%s\n%s\n%s동 %s\n%s/%s/%.1f\n%s\n%s",
                    realestateTypeName,
                    tradeTypeName,
                    realtorName,
                    kakaoAddress,
                    apartName,
                    dong,
                    floorAndHo,
                    warrantPrice,
                    rentPrice,
                    monthlyManagementCost, // 원 -> 만원 변환
                    "https://new.land.naver.com/offices?articleNo=" + articleNo,
                    "https://map.naver.com/p/search/" + kakaoAddress.replaceAll(" ", "%20")
            );
        } else {
            return String.format(
                    "네이버 %s %s\n%s\n%s\n%s\n%s동 %s\n%s억\n%s\n%s",
                    realestateTypeName,
                    tradeTypeName,
                    realtorName,
                    kakaoAddress,
                    apartName,
                    dong,
                    floorAndHo,
                    dealPrice,
                    "https://new.land.naver.com/offices?articleNo=" + articleNo,
                    "https://map.naver.com/p/search/" + kakaoAddress.replaceAll(" ", "%20")
            );
        }
    }


    private ArrayList<String> getApartItemList(String complexNo) {

        ArrayList<String> apartItemNoList = new ArrayList<>();
        String urlFormat = "/api/articles/complex/%s?"
                + "realEstateType=APT:ABYG:JGC:PRE&"
                + "tradeType=&"
                + "tag=::::::::&"
                + "rentPriceMin=0&"
                + "rentPriceMax=900000000&"
                + "priceMin=0&"
                + "priceMax=900000000&"
                + "areaMin=0&"
                + "areaMax=900000000&"
                + "oldBuildYears=&"
                + "recentlyBuildYears=&"
                + "minHouseHoldCount=&"
                + "maxHouseHoldCount=&"
                + "showArticle=false&"
                + "sameAddressGroup=false&"
                + "minMaintenanceCost=&"
                + "maxMaintenanceCost=&"
                + "priceType=RETAIL&"
                + "directions=&"
                + "pageSize=100&"
                + "complexNo=%s&"
                + "buildingNos=&"
                + "areaNos=&"
                + "type=list&"
                + "order=rank&"
                + "page=";

        int count = 1;
        String url = String.format(urlFormat, complexNo, complexNo);
        while (true) {
            Map<String, Object> response = webClient.get()
                    .uri(url + count)
                    .headers(this::makeNaverHeaders)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .blockOptional()
                    .orElseThrow(() -> new RuntimeException("네이버 매물 정보를 가져오는 데 실패했습니다: " + complexNo));

            ArrayList<Map> articleList = (ArrayList) response.get("articleList");

            for (Map article : articleList) {
                Object articleNo = article.get("articleNo");
                if (articleNo != null) {
                    apartItemNoList.add(articleNo.toString());
                }
            }

            if (!(Boolean) response.getOrDefault("isMoreData", false)) {
                break;
            }
            count++;
        }
        return apartItemNoList;
    }

    private ApartItemDto getApartItem(String articleNo) {
        String url = "/api/articles/" + articleNo + "?complexNo=";
        Map<String, Object> article = webClient.get()
                .uri(url)
                .headers(this::makeNaverHeaders)
                .retrieve()
                .bodyToMono(Map.class)
                .blockOptional()
                .orElseThrow(() -> new RuntimeException("네이버 매물 정보를 가져오는 데 실패했습니다: " + articleNo));

        // 필요한 데이터 추출
        Map<String, Object> articleDetail = (Map<String, Object>) article.get("articleDetail");
        Map<String, Object> articlePrice = (Map<String, Object>) article.get("articlePrice");
        Map<String, Object> articleFloor = (Map<String, Object>) article.get("articleFloor");
        Map<String, Object> articleSpace = (Map<String, Object>) article.get("articleSpace");
        Map<String, Object> articleRealtor = (Map<String, Object>) article.get("articleRealtor");
        Map<String, Object> landPrice = (Map<String, Object>) article.get("landPrice");

        Double longitude = Optional.ofNullable(articleDetail.get("longitude"))
                .map(Object::toString)
                .map(Double::parseDouble)
                .orElse(null);
        Double latitude = Optional.ofNullable(articleDetail.get("latitude"))
                .map(Object::toString)
                .map(Double::parseDouble)
                .orElse(null);

        // 중개사 이름 설정 (null이면 "직거래")
        String realtorName = Optional.ofNullable(articleRealtor)
                .map(realtor -> realtor.get("realtorName"))
                .map(Object::toString)
                .orElse("직거래");

        String tradeTypeName = (String) articleDetail.get("tradeTypeName");
        String address = kakaoService.getKakaoAddress(longitude, latitude);
        String aptName = (String) articleDetail.get("aptName");

        String originBuildingName = Optional.ofNullable(articleDetail.get("originBuildingName"))
                .map(Object::toString)
                .orElse("");

        return new ApartItemDto(
                null,
                articleNo,
                address,
                originBuildingName,
                (String) articleFloor.get("correspondingFloorCount"),
                (String) landPrice.get("lineNo"),
                Optional.ofNullable(articleSpace.get("exclusiveSpace"))
                        .map(Object::toString)
                        .map(BigDecimal::new)
                        .orElse(BigDecimal.ZERO),
                Optional.ofNullable(articlePrice.get("dealPrice"))
                        .map(Object::toString)
                        .map(BigDecimal::new)
                        .orElse(BigDecimal.ZERO),
                Optional.ofNullable(articlePrice.get("warrantPrice"))
                        .map(Object::toString)
                        .map(BigDecimal::new)
                        .orElse(BigDecimal.ZERO),
                Optional.ofNullable(articlePrice.get("rentPrice"))
                        .map(Object::toString)
                        .map(BigDecimal::new)
                        .orElse(BigDecimal.ZERO),
                Optional.ofNullable(articleDetail.get("monthlyManagementCost"))
                        .map(Object::toString)
                        .map(BigDecimal::new)
                        .map(value -> value.divide(BigDecimal.valueOf(10000), 1, RoundingMode.HALF_UP)) // 10000으로 나누고 반올림
                        .orElse(BigDecimal.ZERO),
                tradeTypeName,
                LocalDateTime.now(), // 현재 시간으로 설정
                aptName,
                realtorName
        );
    }

    public SanggaItemDto getItem(String articleNo) {
        String url = "/api/articles/" + articleNo + "?complexNo=";

        Map<String, Object> article = webClient.get()
                .uri(url)
                .headers(this::makeNaverHeaders)
                .retrieve()
                .bodyToMono(Map.class)
                .blockOptional()
                .orElseThrow(() -> new RuntimeException("네이버 매물 정보를 가져오는 데 실패했습니다: " + articleNo));

        // 필요한 데이터 추출
        Map<String, Object> articleDetail = (Map<String, Object>) article.get("articleDetail");
        Map<String, Object> articlePrice = (Map<String, Object>) article.get("articlePrice");
        Map<String, Object> articleFloor = (Map<String, Object>) article.get("articleFloor");
        Map<String, Object> articleSpace = (Map<String, Object>) article.get("articleSpace");
        Map<String, Object> articleRealtor = (Map<String, Object>) article.get("articleRealtor");
        List<Map<String, Object>> articlePhotos = (List<Map<String, Object>>) article.get("articlePhotos");
        Map<String, Object> articleAddition = (Map<String, Object>) article.get("articleAddition");
        Map<String, Object> administrationCostInfo = (Map<String, Object>) article.get("administrationCostInfo");

        // 중개사 이름 설정 (null이면 "직거래")
        String realtorName = Optional.ofNullable(articleRealtor)
                .map(realtor -> realtor.get("realtorName"))
                .map(Object::toString)
                .orElse("직거래");

        Double longitude = Optional.ofNullable(articleDetail.get("longitude"))
                .map(Object::toString)
                .map(Double::parseDouble)
                .orElse(null);
        Double latitude = Optional.ofNullable(articleDetail.get("latitude"))
                .map(Object::toString)
                .map(Double::parseDouble)
                .orElse(null);

        String title = Optional.ofNullable(articleDetail.get("articleFeatureDescription"))
                .map(Object::toString)
                .orElse("");

        String realtorId = Optional.ofNullable(articleRealtor)
                .map(realtor -> realtor.get("realtorId"))
                .map(Object::toString)
                .orElse("직거래");


        String description = (String) articleDetail.get("detailDescription");
        String tradeTypeName = (String) articleDetail.get("tradeTypeName");
        String realestateTypeName = (String) articleDetail.get("realestateTypeName");
        String address = kakaoService.getKakaoAddress(longitude, latitude);
        String articleTypeCode = (String) articleDetail.get("articleTypeCode");


        // ===== 광고검색 결과용 추가 필드 추출 =====

        // 1. hasPhoto - 사진 존재 여부
        String hasPhoto = (articlePhotos != null && !articlePhotos.isEmpty()) ? "O" : "X";

        // 2. url - 매물 URL 생성
        String itemUrl = String.format(
                "https://new.land.naver.com/offices?ms=%s,%s,17&a=SG:SMS:GJCG:APTHGJ:GM:TJ&b=A1&e=RETAIL&articleNo=%s",
                latitude, longitude, articleNo
        );

        // 3. registerDate - 등록일 (yyyyMMdd → yyyy.MM.dd)
        String registerDate = Optional.ofNullable(articleDetail.get("exposeStartYMD"))
                .map(Object::toString)
                .map(date -> {
                    if (date.length() == 8) {
                        return date.substring(0, 4) + "." + date.substring(4, 6) + "." + date.substring(6, 8);
                    }
                    return date;
                })
                .orElse("");

        // 4. moveInDate - 입주가능일 (moveInPossibleYmd가 없으면 moveInTypeName 사용)
        String moveInDateRaw = Optional.ofNullable(articleDetail.get("moveInPossibleYmd"))
                .map(Object::toString)
                .orElse(Optional.ofNullable(articleDetail.get("moveInTypeName"))
                        .map(Object::toString)
                        .orElse(""));
        String moveInDate = convertToYyMmDd(moveInDateRaw);

        // 5. verificationTypeName - 확인 유형 ("소유자", "집주인", "일반")
        String verificationTypeName = Optional.ofNullable(articleDetail.get("verificationTypeName"))
                .map(Object::toString)
                .map(type -> {
                    if ("소유자".equals(type) || "집주인".equals(type)) {
                        return type;
                    }
                    return "일반";
                })
                .orElse("일반");

        // 5-1. verificationTypeCode - 확인 유형 코드
        String verificationTypeCode = Optional.ofNullable(articleDetail.get("verificationTypeCode"))
                .map(Object::toString)
                .orElse("");

        // 6. establishRegistrationNo - 중개사 등록번호
        String establishRegistrationNo = Optional.ofNullable(articleRealtor)
                .map(realtor -> realtor.get("establishRegistrationNo"))
                .map(Object::toString)
                .orElse("");

        // 7. cpName - CP 이름 (네이버의 경우)
        String cpName = Optional.ofNullable(articleAddition)
                .map(addition -> addition.get("cpName"))
                .map(Object::toString)
                .orElse("");

        // 8. cpPcArticleUrl - CP 매물 URL (실제 플랫폼 URL)
        String cpPcArticleUrl = Optional.ofNullable(articleAddition)
                .map(addition -> addition.get("cpPcArticleUrl"))
                .map(Object::toString)
                .orElse("");

        SanggaItemDto dto = new SanggaItemDto(
                null, // id는 DB 저장 시 자동 생성
                Optional.ofNullable(articleDetail.get("articleNo")).orElse("").toString(),
                address,
                Optional.ofNullable(articleFloor.get("correspondingFloorCount"))
                        .map(Object::toString)
                        .map(floor -> floor.replace("B", "-")) // "B1" -> "-1", "B" -> "-"
                        .filter(floor -> floor.matches("-?\\d+")) // 숫자(- 포함)만 허용
                        .map(Integer::parseInt)
                        .orElse(null),
                Optional.ofNullable(articleSpace.get("exclusiveSpace"))
                        .map(Object::toString)
                        .map(BigDecimal::new)
                        .orElse(BigDecimal.ZERO),
                Optional.ofNullable(articlePrice.get("dealPrice"))
                        .map(Object::toString)
                        .map(BigDecimal::new)
                        .orElse(BigDecimal.ZERO),
                Optional.ofNullable(articlePrice.get("warrantPrice"))
                        .map(Object::toString)
                        .map(BigDecimal::new)
                        .orElse(BigDecimal.ZERO),
                Optional.ofNullable(articlePrice.get("rentPrice"))
                        .map(Object::toString)
                        .map(BigDecimal::new)
                        .orElse(BigDecimal.ZERO),
                Optional.ofNullable(articleDetail.get("monthlyManagementCost"))
                        .map(Object::toString)
                        .map(BigDecimal::new)
                        .map(value -> value.divide(BigDecimal.valueOf(10000), 1, RoundingMode.HALF_UP)) // 10000으로 나누고 반올림
                        .orElse(BigDecimal.ZERO),
                longitude,
                latitude,
                LocalDateTime.now(), // 현재 시간으로 설정
                "네이버", // 플랫폼 고정값
//                agentCustomer.getRegionName(),
                realtorName,
                title,
                description,
                tradeTypeName,
                realestateTypeName
        );

        // 광고검색 결과용 추가 필드 설정
        dto.setHasPhoto(hasPhoto);
        dto.setUrl(itemUrl);
        dto.setCpPcArticleUrl(cpPcArticleUrl);
        dto.setRegisterDate(registerDate);
        dto.setMoveInDate(moveInDate);
        dto.setVerificationTypeName(verificationTypeName);
        dto.setVerificationTypeCode(verificationTypeCode);
        dto.setEstablishRegistrationNo(establishRegistrationNo);
        dto.setCpName(cpName);
        dto.setArticleTypeCode(articleTypeCode);

        // 공실클럽 조회용 추가 필드
        dto.setRealtorId(realtorId);
        dto.setPhotoCount(articlePhotos != null ? articlePhotos.size() : 0);

        return dto;
    }

    // 입주가능일 변환 헬퍼 메서드 (Python의 convert_to_yy_mm_dd와 동일)
    private String convertToYyMmDd(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return "";
        }

        // "즉시" 또는 "NOW" 포함 시 "즉시 입주" 반환
        if (dateStr.contains("즉시") || "NOW".equals(dateStr)) {
            return "즉시 입주";
        }

        // 정규식으로 숫자만 추출
        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcher = pattern.matcher(dateStr);
        List<String> numbers = new ArrayList<>();
        while (matcher.find()) {
            numbers.add(matcher.group());
        }

        // 숫자가 3개인 경우 (년, 월, 일)
        if (numbers.size() == 3) {
            return numbers.get(0) + "." + numbers.get(1) + "." + numbers.get(2);
        }

        // 숫자가 1개이고 8자리인 경우 (yyyyMMdd)
        if (numbers.size() == 1 && numbers.get(0).length() == 8) {
            String num = numbers.get(0);
            String year = num.substring(0, 4);
            String month = num.substring(4, 6);
            String day = num.substring(6, 8);
            return year + "." + month + "." + day;
        }

        // 그 외는 그대로 반환
        return dateStr;
    }

    private ArrayList<String> getItemListByPnuForHostel(String pnu) {
        ArrayList<String> articleList = new ArrayList<>();
        int count = 1;
        boolean hasMoreData;

        do {
            String url = String.format("/api/articles?cortarNo=%s&zoom=17&order=dateDesc&realEstateType=SG" +
                            "&tradeType=B2&tag=::::::::&rentPriceMin=0&rentPriceMax=4000&priceMin=0&priceMax=900000000" +
                            "&areaMin=231&areaMax=900000000&oldBuildYears&recentlyBuildYears&minHouseHoldCount" +
                            "&maxHouseHoldCount&showArticle=false&sameAddressGroup=false&minMaintenanceCost" +
                            "&maxMaintenanceCost&priceType=RETAIL&directions=&articleState&pageSize=1000&page=%d",
                    pnu, count);

            Map<String, Object> response = webClient.get()
                    .uri(url)
                    .headers(this::makeNaverHeaders)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !response.containsKey("articleList")) {
                break;
            }

            List<Map<String, Object>> resArticleList = (List<Map<String, Object>>) response.get("articleList");

            resArticleList.forEach(article -> {
                //면적 필터링
                Integer area2 = (Integer) article.get("area2");
                Double pyeong = area2 / 3.3;
                String rentPrcStr = (String) article.get("rentPrc");
                rentPrcStr = rentPrcStr.replace(",", "");
                Integer rentPrc = Integer.parseInt(rentPrcStr);
                Integer pricePerPyeong = 5;
                if (pyeong < 70 || rentPrc / pyeong > pricePerPyeong) {
                    return;
                }

                //층수 필터링
                String floorInfo = (String) article.get("floorInfo");
                String floorStr = floorInfo.split("/")[0].replace("B", "-");
                try {
                    Integer floor = Integer.parseInt(floorStr);
                    if (0 > floor || floor >= 5) {
                        return;
                    }
                } catch (NumberFormatException e) {
                }


                articleList.add((String) article.get("articleNo"));
            });

            hasMoreData = (Boolean) response.getOrDefault("isMoreData", false);
            count++;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } while (hasMoreData);
        return articleList;
    }

    private ArrayList<String> getItemListByPnu(String pnu) {
        ArrayList<String> articleList = new ArrayList<>();
        int count = 1;
        boolean hasMoreData;

        do {
            String url = String.format("/api/articles?cortarNo=%s&zoom=17&order=dateDesc&realEstateType=GJCG:TJ" +
                            "&tradeType=B2&tag=::::::::&rentPriceMin=200&rentPriceMax=4000&priceMin=0&priceMax=900000000" +
                            "&areaMin=420&areaMax=900000000&oldBuildYears&recentlyBuildYears&minHouseHoldCount" +
                            "&maxHouseHoldCount&showArticle=false&sameAddressGroup=false&minMaintenanceCost" +
                            "&maxMaintenanceCost&priceType=RETAIL&directions=&articleState&pageSize=1000&page=%d",
                    pnu, count);

            Map<String, Object> response = webClient.get()
                    .uri(url)
                    .headers(this::makeNaverHeaders)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !response.containsKey("articleList")) {
                break;
            }

            List<Map<String, Object>> resArticleList = (List<Map<String, Object>>) response.get("articleList");

            resArticleList.forEach(article -> {
                articleList.add((String) article.get("articleNo"));
            });

            hasMoreData = (Boolean) response.getOrDefault("isMoreData", false);
            count++;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } while (hasMoreData);
        return articleList;
    }

    private ArrayList<String> getItemList(AgentCustomer agentCustomer) {
        Polygon polygon = agentCustomer.getPolygon();
        BoundingBox boundingBox = PolygonUtils.getBoundingBox(polygon);

        ArrayList<String> articleList = new ArrayList<>();

        // 조회할 realEstateType 목록
        List<String> realEstateTypes = new ArrayList<>();
        String tradeType = "";

        if (agentCustomer.getKind().equals("상가")) {
            realEstateTypes.add("SG:SMS:GJCG:APTHGJ:GM:TJ");
            tradeType = "B2";
        } else if (agentCustomer.getKind().equals("공장창고")) {
            realEstateTypes.add("GJCG:TJ");
        } else if (agentCustomer.getKind().equals("소유자")){
            // 소유자는 두 가지 타입 모두 조회
            realEstateTypes.add("SG:SMS:GJCG:APTHGJ:GM:TJ");
            realEstateTypes.add("APT:OPST:ABYG:OBYG:GM:OR:VL:DDDGG:JWJT:SGJT:HOJT");
            tradeType = "A1:B1:B2";
        }

        // 각 realEstateType에 대해 조회
        for (String realEstateType : realEstateTypes) {
            int count = 1;
            String url = "/api/articles?zoom=15&leftLon=" + boundingBox.getMinLng() + "&rightLon=" + boundingBox.getMaxLng() + "&topLat=" + boundingBox.getMaxLat() + "&bottomLat=" + boundingBox.getMinLat() + "&order=rank&realEstateType=" + realEstateType + "&tradeType=" + tradeType + "&tag=::::::::&rentPriceMin=0&rentPriceMax=900000000&priceMin=0&priceMax=900000000&areaMin=0&areaMax=900000000&oldBuildYears&recentlyBuildYears&minHouseHoldCount&maxHouseHoldCount&showArticle=false&sameAddressGroup=false&minMaintenanceCost&maxMaintenanceCost&priceType=RETAIL&directions=&articleState&pageSize=1000&page=";

            while (true) {
                Map<String, Object> response = webClient.get()
                        .uri(url + count)
                        .headers(this::makeNaverHeaders)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();

                if (response == null || !response.containsKey("articleList")) {
                    break;
                }

                List<Map<String, Object>> resArticleList = (List<Map<String, Object>>) response.get("articleList");

                for (Map<String, Object> article : resArticleList) {
                    // 소유자인 경우 특정 플랫폼/인증타입 매물만 필터링
                    if (agentCustomer.getKind().equals("소유자")) {
                        String cpName = (String) article.get("cpName");
                        String verificationTypeCode = (String) article.get("verificationTypeCode");
                        List<String> targetPlatforms = Arrays.asList(
                            "부동산써브", "부동산뱅크", "우리집부동산", "선방", "공실클럽", "부동산포스"
                        );
                        List<String> targetVerifications = Arrays.asList(
                                "NDOC1", "NDOC2", "OWNER"
                        );

                        boolean isPlatformMatch = cpName != null && targetPlatforms.contains(cpName);
                        boolean isVerificationMatch = verificationTypeCode != null && targetVerifications.contains(verificationTypeCode);
                        // 플랫폼 또는 인증타입 둘 중 하나라도 안 맞으면 건너뜀 (둘 다 맞아야 통과)
                        if (!isPlatformMatch || !isVerificationMatch) {
                            continue;
                        }
                    }
                    //층 체크
                    if (agentCustomer.isMinFloorPresent()) {
                        try {
                            String floorInfo = (String) article.get("floorInfo");
                            String floorStr = floorInfo.split("/")[0].replace("B", "-");
                            Integer floor = Integer.parseInt(floorStr);
                            if (agentCustomer.getMinFloor() > floor || agentCustomer.getMaxFloor() < floor) {
                                continue;
                            }
                        } catch (Exception e) {

                        }
                    }

                    // String -> Double 변환
                    Double lng = Double.parseDouble((String) article.get("longitude"));
                    Double lat = Double.parseDouble((String) article.get("latitude"));

                    if (lat == 0 || PolygonUtils.isInsidePolygon(agentCustomer.getPolygon(), lng, lat)) {
                        String articleNo = (String) article.get("articleNo");
                        // 중복 방지
                        if (!articleList.contains(articleNo)) {
                            articleList.add(articleNo);
                        }
                    }
                }
                if (!(Boolean) response.getOrDefault("isMoreData", false)) {
                    break;
                }
                count++;
            }
        }
        return articleList;
    }

    @Override
    public ArrayList<String> getItemListByCoord(AgentCustomer agentCustomer) {
        Polygon polygon = agentCustomer.getPolygon();
        BoundingBox boundingBox = PolygonUtils.getBoundingBox(polygon);

        ArrayList<String> articleList = new ArrayList<>();
        int count = 1;
        String realEstateType = "";
        String tradeType = "";
        if (agentCustomer.getKind().equals("상가")) {
            realEstateType = "SG:SMS:GJCG:APTHGJ:GM:TJ";
            tradeType = "B2";
        } else if (agentCustomer.getKind().equals("공장창고")) {
            realEstateType = "GJCG:TJ";
        }
        String url = "/api/articles?zoom=15&leftLon=" + boundingBox.getMinLng() + "&rightLon=" + boundingBox.getMaxLng() + "&topLat=" + boundingBox.getMaxLat() + "&bottomLat=" + boundingBox.getMinLat() + "&order=rank&realEstateType=" + realEstateType + "&tradeType=" + tradeType + "&tag=::::::::&rentPriceMin=0&rentPriceMax=900000000&priceMin=0&priceMax=900000000&areaMin=0&areaMax=900000000&oldBuildYears&recentlyBuildYears&minHouseHoldCount&maxHouseHoldCount&showArticle=false&sameAddressGroup=false&minMaintenanceCost&maxMaintenanceCost&priceType=RETAIL&directions=&articleState&pageSize=1000&page=";

        while (true) {
            Map<String, Object> response = webClient.get()
                    .uri(url + count)
                    .headers(this::makeNaverHeaders)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !response.containsKey("articleList")) {
                break;
            }

            List<Map<String, Object>> resArticleList = (List<Map<String, Object>>) response.get("articleList");

            for (Map<String, Object> article : resArticleList) {
                //층 체크
                if (agentCustomer.isMinFloorPresent()) {
                    try {
                        String floorInfo = (String) article.get("floorInfo");
                        String floorStr = floorInfo.split("/")[0].replace("B", "-");
                        Integer floor = Integer.parseInt(floorStr);
                        if (agentCustomer.getMinFloor() > floor || agentCustomer.getMaxFloor() < floor) {
                            continue;
                        }
                    } catch (Exception e) {

                    }
                }

                // String -> Double 변환
                Double lng = Double.parseDouble((String) article.get("longitude"));
                Double lat = Double.parseDouble((String) article.get("latitude"));

                if (lat == 0 || PolygonUtils.isInsidePolygon(agentCustomer.getPolygon(), lng, lat)) {
                    articleList.add((String) article.get("articleNo"));
                }
            }
            if (!(Boolean) response.getOrDefault("isMoreData", false)) {
                break;
            }
            count++;
        }
        return articleList;
    }
    @Override
    public ArrayList<String> getItemListWithCustomer(CustomerInfoEntity customerInfo) {
        Polygon polygon = customerInfo.getPolygon();
        BoundingBox boundingBox = PolygonUtils.getBoundingBox(polygon);

        ArrayList<String> articleList = new ArrayList<>();
        int count = 1;
        String realEstateType = "";
        String tradeType = "";
        if (customerInfo.getKind().equals("상가")) {
            realEstateType = "SG:SMS:GJCG:APTHGJ:GM:TJ";
            tradeType = "B2";
        } else if (customerInfo.getKind().equals("공장창고")) {
            realEstateType = "GJCG:TJ";
        }
        String url = "/api/articles?zoom=15&leftLon=" + boundingBox.getMinLng() + "&rightLon=" + boundingBox.getMaxLng() + "&topLat=" + boundingBox.getMaxLat() + "&bottomLat=" + boundingBox.getMinLat() + "&order=rank&realEstateType=" + realEstateType + "&tradeType=" + tradeType + "&tag=::::::::&rentPriceMin=0&rentPriceMax=900000000&priceMin=0&priceMax=900000000&areaMin=0&areaMax=900000000&oldBuildYears&recentlyBuildYears&minHouseHoldCount&maxHouseHoldCount&showArticle=false&sameAddressGroup=false&minMaintenanceCost&maxMaintenanceCost&priceType=RETAIL&directions=&articleState&pageSize=1000&page=";

        while (true) {
            Map<String, Object> response = webClient.get()
                    .uri(url + count)
                    .headers(this::makeNaverHeaders)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !response.containsKey("articleList")) {
                break;
            }

            List<Map<String, Object>> resArticleList = (List<Map<String, Object>>) response.get("articleList");

            for (Map<String, Object> article : resArticleList) {
                //면적 필터링
                Integer area2 = (Integer) article.get("area2");
                Double pyeong = area2 / 3.3;
                String rentPrcStr = (String) article.get("rentPrc");
                rentPrcStr = rentPrcStr.replace(",", "");
                if (BigDecimal.valueOf(pyeong).compareTo(customerInfo.getMinArea()) < 0) {
                    continue;
                }

                if (customerInfo.isMinFloorPresent()) {
                    try {
                        String floorInfo = (String) article.get("floorInfo");
                        String floorStr = floorInfo.split("/")[0].replace("B", "-");
                        Integer floor = Integer.parseInt(floorStr);
                        if (customerInfo.getMinFloor() > floor || customerInfo.getMaxFloor() < floor) {
                            continue;
                        }
                    } catch (Exception e) {

                    }
                }
                // String -> Double 변환
                Double lng = Double.parseDouble((String) article.get("longitude"));
                Double lat = Double.parseDouble((String) article.get("latitude"));

                if (lat == 0 || PolygonUtils.isInsidePolygon(customerInfo.getPolygon(), lng, lat)) {
                    articleList.add((String) article.get("articleNo"));
                }
            }

            if (!(Boolean) response.getOrDefault("isMoreData", false)) {
                break;
            }
            count++;
        }
        return articleList;
    }
//
//    private String fetchNaverToken() {
//        String tokenUrl = "/offices?ms=37.4706935,126.9380634,17&a=SG:SMS:GJCG:APTHGJ:GM:TJ&e=RETAIL";
//
//        String body = webClient.get()
//                .uri(tokenUrl)
//                .headers(this::makeTokenHeaders)
//                .retrieve()
//                .bodyToMono(String.class)
//                .block();
//
//        String token = null;
//        if (body != null) {
//            int start = body.indexOf("\"token\":\"");
//            if (start != -1) {
//                start += 9; // "token":" 길이
//                int end = body.indexOf("\"", start);
//                if (end != -1) {
//                    token = body.substring(start, end);
//                }
//            }
//        }
//        return token;
//    }

    private HttpHeaders makeInitHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Accept", "*/*");
        headers.add("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7");
        headers.add("Connection", "keep-alive");
        headers.add("Sec-Ch-Ua-Platform", "Windows");
        headers.add("Referer", "https://new.land.naver.com");
        headers.add("Host", "new.land.naver.com");
        headers.add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/113.0.0.0 Safari/537.36");

        String tokenUrl = "/offices?a=SG:SMS:GJCG:APTHGJ:GM:TJ&e=RETAIL";

        ClientResponse response = webClient.get()
                .uri(tokenUrl)
                .headers(h -> h.addAll(headers)) // headers 변수 그대로 복사
                .exchange()
                .block();
        String body = response.bodyToMono(String.class).block();

        String token = null;
        if (body != null) {
            int start = body.indexOf("\"token\":\"");
            if (start != -1) {
                start += 9; // "token":" 길이
                int end = body.indexOf("\"", start);
                if (end != -1) {
                    token = body.substring(start, end);
                }
            }
        }
        String cookieStr = String.join("; ",
                response.headers()
                        .asHttpHeaders()
                        .getOrDefault("Set-Cookie", List.of())
                        .stream()
                        .map(c -> c.split(";", 2)[0]) // path, secure 등 제거
                        .toList()
        );

        headers.add("Cookie", cookieStr);
        headers.add("Authorization", "Bearer " + token);
        return headers;
    }


    private void makeTokenHeaders(HttpHeaders headers) {
        headers.add("Accept", "*/*");
        headers.add("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7");
        headers.add("Connection", "keep-alive");
        headers.add("Sec-Ch-Ua-Platform", "Windows");
        headers.add("Referer", "https://new.land.naver.com");
        headers.add("Host", "new.land.naver.com");
        headers.add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/113.0.0.0 Safari/537.36");
    }


    private void makeNaverHeaders(HttpHeaders headers) {
        // initHeaders에 담아둔 쿠키/토큰/기본 UA 등을 그대로 복사
        headers.addAll(this.initHeaders);
    }


    public String makeNaverText(SanggaItemDto sanggaItemDto) {
        // 월 관리비 (null 방지)
        double monthlyManagementCost = sanggaItemDto.getManagementFee().doubleValue();

        // 보증금, 월세 (null 방지)
        String warrantPrice = Optional.ofNullable(sanggaItemDto.getDeposit())
                .map(BigDecimal::toPlainString)
                .orElse("0");
        String rentPrice = Optional.ofNullable(sanggaItemDto.getMonthlyFee())
                .map(BigDecimal::toPlainString)
                .orElse("0");
        String dealPrice = Optional.ofNullable(sanggaItemDto.getDealPrice())
                .map(p -> p.divide(BigDecimal.valueOf(10000), 2, RoundingMode.HALF_UP))
                .map(BigDecimal::toPlainString)
                .orElse("0");
        // 층수, 중개사 이름
        String floor = Optional.ofNullable(sanggaItemDto.getFloor())
                .map(f -> f + "층")
                .orElse("");

        String realtorName = Optional.ofNullable(sanggaItemDto.getAgentName())
                .orElse("");

        // 위도, 경도 (null 방지)
        Double lng = Optional.ofNullable(sanggaItemDto.getLongitude()).orElse(0.0);
        Double lat = Optional.ofNullable(sanggaItemDto.getLatitude()).orElse(0.0);

        // Kakao API를 통해 주소 변환
        String kakaoAddress = Optional.ofNullable(sanggaItemDto.getAddress()).orElse("");
        String articleNo = Optional.ofNullable(sanggaItemDto.getItemId()).orElse("");
        String realestateTypeName = Optional.ofNullable(sanggaItemDto.getRealestateTypeName()).orElse("");
        String tradeTypeName = Optional.ofNullable(sanggaItemDto.getTradeTypeName()).orElse("");

        if (tradeTypeName.equals("월세")) {
            // 최종 텍스트 생성 (String.format() 사용)
            return String.format(
                    "네이버 %s %s\n%s\n%s\n%s\n%s/%s/%.1f\n%s\n%s",
                    realestateTypeName,
                    tradeTypeName,
                    realtorName,
                    kakaoAddress,
                    floor,
                    warrantPrice,
                    rentPrice,
                    monthlyManagementCost, // 원 -> 만원 변환
                    "https://new.land.naver.com/offices?articleNo=" + articleNo,
                    "https://map.naver.com/p/search/" + kakaoAddress.replaceAll(" ", "%20")
            );
        } else {
            return String.format(
                    "네이버 %s %s\n%s\n%s\n%s\n%s억\n%s\n%s",
                    realestateTypeName,
                    tradeTypeName,
                    realtorName,
                    kakaoAddress,
                    floor,
                    dealPrice,
                    "https://new.land.naver.com/offices?articleNo=" + articleNo,
                    "https://map.naver.com/p/search/" + kakaoAddress.replaceAll(" ", "%20")
            );
        }

    }

    private String makeIamgroundNaverText(SanggaItemDto sanggaItemDto) {

        // 월 관리비 (null 방지)
        double monthlyManagementCost = sanggaItemDto.getManagementFee().doubleValue();

        // 보증금, 월세 (null 방지)
        String warrantPrice = Optional.ofNullable(sanggaItemDto.getDeposit())
                .map(BigDecimal::toPlainString)
                .orElse("0");
        String rentPrice = Optional.ofNullable(sanggaItemDto.getMonthlyFee())
                .map(BigDecimal::toPlainString)
                .orElse("0");

        String realtorName = Optional.ofNullable(sanggaItemDto.getAgentName())
                .orElse("N/A");

        // 위도, 경도 (null 방지)
        Double lng = Optional.ofNullable(sanggaItemDto.getLongitude()).orElse(0.0);
        Double lat = Optional.ofNullable(sanggaItemDto.getLatitude()).orElse(0.0);

        // Kakao API를 통해 주소 변환
        String kakaoAddress = Optional.ofNullable(sanggaItemDto.getAddress()).orElse("");
        String articleNo = Optional.ofNullable(sanggaItemDto.getItemId()).orElse("");

        String td = sanggaItemDto.getTitle() + "\n" + sanggaItemDto.getDescription();
        String gptText = cleanDescription(td);

        // 최종 텍스트 생성 (String.format() 사용)
        return String.format(
                "네이버 상가 월세\n%s\n%s\n%s/%s/%.1f\n%s\n%s\n%s",
                realtorName,
                kakaoAddress,
                warrantPrice,
                rentPrice,
                monthlyManagementCost, // 원 -> 만원 변환
                gptText,
                "https://new.land.naver.com/offices?articleNo=" + articleNo,
                "https://map.naver.com/p/search/" + kakaoAddress.replaceAll(" ", "%20")
        );
    }

    public String cleanDescription(String text) {
        // 패턴 목록
        String[] patternsToKeep = {
                "\\d{1,5}[ ]?(p|P|py|PY|ㅍ|평|㎡|제곱미터)",
                "(약[ ]?|전용[ ]?|전[ ]?|실사용[ ]?|실[ ]?|실면적[ ]?|전용면적[ ]?|전용 면적[ ]?|임대면적[ ]?|임대 면적[ ]?|실평수[ ]?|면적[ ]?)\\s?\\d{1,5}",
                "\\d{1,5}[ ]?(전용|면적)",
                "(면적|전용)[ ]?:[ ]?\\d{1,5}",
                "(약[ ]?|전용[ ]?|전[ ]?|실사용[ ]?|실[ ]?|실면적[ ]?|전용면적[ ]?|전용 면적[ ]?|임대면적[ ]?|임대 면적[ ]?|실평수[ ]?|면적[ ]?)\\d{1,5}",
                "(실)?\\d{1,5}(p|P|py|PY|ㅍ|평|㎡|제곱미터)"
        };
        Pattern digitPattern = Pattern.compile("\\d");
        Pattern combinedPattern = Pattern.compile(String.join("|", patternsToKeep));

        // 입력 문자열을 줄 단위로 나누기
        String[] lines = text.split("\n");
        List<String> cleanedLines = new ArrayList<>();

        // 각 라인 처리
        for (String line : lines) {
            // 특정 키워드가 포함된 라인은 건너뛰기
            if (containsAny(line, new String[]{"토지면적", "대지면적", "연면적", "건축면적", "대지 :", "대지:"})) {
                continue;
            }

            // 숫자가 없는 라인은 건너뛰기
            Matcher digitMatcher = digitPattern.matcher(line);
            if (!digitMatcher.find()) {
                continue;
            }

            // 패턴에 맞는 라인만 추가
            Matcher matcher = combinedPattern.matcher(line);
            if (matcher.find()) {
                cleanedLines.add(line.trim());
            } else if (line.matches("\\d{1,5}[,]?\\d{0,3}[ ]?(p|P|py|PY|ㅍ|평|㎡|제곱미터)\\b")) {
                cleanedLines.add(line.trim());
            }
        }

        // 결과 문자열로 반환
        return String.join("\n", cleanedLines);
    }

    private static boolean containsAny(String line, String... keywords) {
        for (String keyword : keywords) {
            if (line.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    // ========== 좌표 범위로 매물 검색 ==========

    @Override
    public ArrayList<String> getItemListByCoord(double minLat, double minLng, double maxLat, double maxLng, String propertyType) {
        ArrayList<String> articleList = new ArrayList<>();
        int count = 1;
        String realEstateType = "";
        String tradeType = "";

        if (propertyType.equals("상가")) {
            realEstateType = "SG:SMS:GJCG:APTHGJ:GM:TJ";
            tradeType = "B2";
        } else if (propertyType.equals("공장창고")) {
            realEstateType = "GJCG:TJ";
            tradeType = "B2";
        } else if (propertyType.equals("주택")) {
            realEstateType = "APT:ABYG:JGC";
            tradeType = "B1:B2:B3";
        }

        String url = "/api/articles?zoom=15&leftLon=" + minLng + "&rightLon=" + maxLng + "&topLat=" + maxLat + "&bottomLat=" + minLat +
                     "&order=rank&realEstateType=" + realEstateType + "&tradeType=" + tradeType +
                     "&tag=::::::::&rentPriceMin=0&rentPriceMax=900000000&priceMin=0&priceMax=900000000&areaMin=0&areaMax=900000000" +
                     "&oldBuildYears&recentlyBuildYears&minHouseHoldCount&maxHouseHoldCount&showArticle=false&sameAddressGroup=false" +
                     "&minMaintenanceCost&maxMaintenanceCost&priceType=RETAIL&directions=&articleState&pageSize=1000&page=";

        while (true) {
            Map<String, Object> response = webClient.get()
                    .uri(url + count)
                    .headers(this::makeNaverHeaders)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .blockOptional()
                    .orElse(null);

            if (response == null || !response.containsKey("articleList")) {
                break;
            }

            List<Map<String, Object>> resArticleList = (List<Map<String, Object>>) response.get("articleList");

            for (Map<String, Object> article : resArticleList) {
                articleList.add((String) article.get("articleNo"));
            }

            if (!(Boolean) response.getOrDefault("isMoreData", false)) {
                break;
            }
            count++;
        }
        return articleList;
    }

    // ========== Geocoding 관련 메서드 ==========

    @Override
    public JsonNode getGeocode(String address) {
        try {
            String url = "/map-geocode/v2/geocode?query=" + address;
            String response = naverMapWebClient.get()
                    .uri(url)
                    .header("X-NCP-APIGW-API-KEY-ID", NAVER_MAP_CLIENT_ID)
                    .header("X-NCP-APIGW-API-KEY", NAVER_MAP_CLIENT_SECRET)
                    .header("Accept", "application/json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .blockOptional()
                    .orElseThrow(() -> new RuntimeException("Geocoding API 응답을 받지 못했습니다: " + address));
            return objectMapper.readTree(response);

        } catch (Exception e) {
            System.err.println("Geocoding 실패: " + e.getMessage());
            throw new RuntimeException("주소를 좌표로 변환하는데 실패했습니다: " + address, e);
        }
    }

    @Override
    public JsonNode getReverseGeocode(double x, double y) {
        try {
            String url = String.format("/map-reversegeocode/v2/gc?coords=%f,%f&orders=addr&output=json", x, y);

            String response = naverMapWebClient.get()
                    .uri(url)
                    .header("X-NCP-APIGW-API-KEY-ID", NAVER_MAP_CLIENT_ID)
                    .header("X-NCP-APIGW-API-KEY", NAVER_MAP_CLIENT_SECRET)
                    .header("Accept", "application/json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .blockOptional()
                    .orElseThrow(() -> new RuntimeException("Reverse Geocoding API 응답을 받지 못했습니다"));

            JsonNode jsonNode = objectMapper.readTree(response);
            return jsonNode.get("results").get(0);

        } catch (Exception e) {
            System.err.println("Reverse Geocoding 실패: " + e.getMessage());
            throw new RuntimeException(String.format("좌표를 주소로 변환하는데 실패했습니다: (%f, %f)", x, y), e);
        }
    }

    @Override
    public String getAddressByLatLng(double lat, double lng) {
        try {
            JsonNode result = getReverseGeocode(lng, lat);

            // 지번 정보 추출
            JsonNode land = result.get("land");
            String jibun = land.get("number1").asText();
            if (land.has("number2") && !land.get("number2").asText().isEmpty()) {
                jibun += "-" + land.get("number2").asText();
            }

            // 지역 정보 추출
            JsonNode region = result.get("region");
            String area2 = region.get("area2").get("name").asText();
            String area3 = region.get("area3").get("name").asText();
            String area4 = region.get("area4").get("name").asText();

            // 주소 조합
            List<String> addressParts = new ArrayList<>();
            if (area2 != null && !area2.isEmpty()) addressParts.add(area2);
            if (area3 != null && !area3.isEmpty()) addressParts.add(area3);
            if (area4 != null && !area4.isEmpty()) addressParts.add(area4);
            if (jibun != null && !jibun.isEmpty()) addressParts.add(jibun);

            return String.join(" ", addressParts);

        } catch (Exception e) {
            System.err.println("주소 변환 실패: " + e.getMessage());
            throw new RuntimeException(String.format("위도/경도를 주소로 변환하는데 실패했습니다: (%f, %f)", lat, lng), e);
        }
    }

    @Override
    public JsonNode getArticlesByRealtorId(String realtorId) {
        String url = "/api/articles?realtorId=" + realtorId + "&pageSize=1000";
        String response = webClient.get()
                .uri(url)
                .headers(this::makeNaverHeaders)
                .retrieve()
                .bodyToMono(String.class)
                .blockOptional()
                .orElseThrow(() -> new RuntimeException("realtorId 매물 목록 조회 실패: " + realtorId));
        try {
            return objectMapper.readTree(response);
        } catch (Exception e) {
            throw new RuntimeException("realtorId 매물 목록 응답 파싱 실패: " + realtorId, e);
        }
    }

    @Override
    public JsonNode getArticleDetailRaw(String articleNo) {
        String url = "/api/articles/" + articleNo;
        String response = webClient.get()
                .uri(url)
                .headers(this::makeNaverHeaders)
                .retrieve()
                .bodyToMono(String.class)
                .blockOptional()
                .orElseThrow(() -> new RuntimeException("매물 상세 조회 실패: " + articleNo));
        try {
            return objectMapper.readTree(response);
        } catch (Exception e) {
            throw new RuntimeException("매물 상세 응답 파싱 실패: " + articleNo, e);
        }
    }

    @Override
    public byte[] getStaticMapImage(List<double[]> coordinates) {
        try {
            if (coordinates == null || coordinates.isEmpty()) {
                throw new RuntimeException("좌표 목록이 비어있습니다.");
            }

            // Static Map API URL 구성
            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append("https://maps.apigw.ntruss.com/map-static/v2/raster");
            urlBuilder.append("?crs=EPSG:4326&scale=2&format=png&w=628&h=427");

            // 마커 추가 (파이썬과 동일한 형식, URL 인코딩 적용)
            for (int i = 0; i < coordinates.size(); i++) {
                double[] coord = coordinates.get(i);
                double lng = coord[0]; // 경도
                double lat = coord[1]; // 위도
                // type:n|viewSizeRatio:0.5|color:red|size:mid|pos:{x} {y}|label:{idx+1}
                String marker = String.format("type:n|viewSizeRatio:0.5|color:red|size:mid|pos:%f %f|label:%d", lng, lat, i + 1);
                String encodedMarker = java.net.URLEncoder.encode(marker, java.nio.charset.StandardCharsets.UTF_8);
                urlBuilder.append("&markers=").append(encodedMarker);
            }

            String url = urlBuilder.toString();
            System.out.println("Static Map URL: " + url);

            // HTTP 요청
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("X-NCP-APIGW-API-KEY-ID", NAVER_MAP_CLIENT_ID)
                    .header("X-NCP-APIGW-API-KEY", NAVER_MAP_CLIENT_SECRET)
                    .GET()
                    .build();

            java.net.http.HttpResponse<byte[]> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200) {
                System.out.println("Static Map 이미지 생성 완료");
                return response.body();
            } else {
                System.err.println("Static Map API 요청 실패: " + response.statusCode() + " - " + new String(response.body()));
                throw new RuntimeException("Static Map API 요청 실패: " + response.statusCode());
            }

        } catch (Exception e) {
            System.err.println("Static Map 생성 실패: " + e.getMessage());
            throw new RuntimeException("Static Map 이미지 생성 실패: " + e.getMessage(), e);
        }
    }




}
