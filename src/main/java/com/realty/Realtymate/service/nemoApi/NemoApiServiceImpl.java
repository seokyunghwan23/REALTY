package com.realty.Realtymate.service.nemoApi;

import com.realty.Realtymate.model.AgentCustomer;
import com.realty.Realtymate.model.BoundingBox;
import com.realty.Realtymate.model.SanggaItemDto;
import com.realty.Realtymate.model.SanggaItemEntity;
import com.realty.Realtymate.service.RepositoryService.ItemInfoService;
import com.realty.Realtymate.service.telegramApi.TelegramApiService;
import com.realty.Realtymate.utils.PolygonUtils;
import com.realty.Realtymate.utils.Utils;
import org.apache.hc.core5.net.URIBuilder;
import org.locationtech.jts.geom.Polygon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class NemoApiServiceImpl implements NemoApiService{
    private final WebClient webClient;

    @Autowired
    TelegramApiService telegramApiService;
    @Autowired
    ItemInfoService itemInfoService;

    public NemoApiServiceImpl() {
        this.webClient = WebClient.builder().baseUrl("https://api.nemoapp.kr").build();
    }
    @Override
    public void alertSanggaNewAd(List<SanggaItemEntity> previousItemList, AgentCustomer agentCustomer) {

        //이전매물 받아오기
        ArrayList<String> previousList = (ArrayList<String>) previousItemList.stream()
                .filter(previousItem -> previousItem.getPlatform().equals("네모"))
                .map(SanggaItemEntity::getItemId)
                .collect(Collectors.toList());

        List<String> articleTypeList = List.of("store", "office");
        articleTypeList.forEach(articleType -> {
            Map<String, Map<String, Object>> nemoCurrentArticles = getItemList(articleType, agentCustomer);
            ArrayList<String> currentList = new ArrayList<>(nemoCurrentArticles.keySet());

            ArrayList<String> newAd = Utils.checkNewAd(previousList, currentList);

            for (String itemId : newAd) {
                // 📌 SanggaItemDto 생성
                SanggaItemDto sanggaItemDto = createSanggaItemDto(articleType, itemId);
                if (sanggaItemDto == null) continue; // API 호출 실패 시 스킵

                // 📌 makeNemoText 사용
                String text = makeNemoText(articleType, itemId, sanggaItemDto);

                // 📌 Telegram 전송
                telegramApiService.sendMessage("-1002057050893", text);

                // 📌 원본 데이터 저장
                itemInfoService.saveSanggaItem(sanggaItemDto);
            }


        });

    }
    private SanggaItemDto createSanggaItemDto(String articleType, String itemId) {
        // 📌 API 호출하여 상세 정보 가져오기
        String url = "https://api.nemoapp.kr/api/" + articleType + "/find-id?number=" + itemId;

        Map<String, Object> itemDetail = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(Map.class)
                .block(); // 동기 처리 (비동기 처리하려면 Flux 사용)

        if (itemDetail == null) return null; // 데이터 없음

        // 📌 건물 정보 가져오기
        Map<String, Object> building = (Map<String, Object>) itemDetail.get("building");
        String fullAddress = (String) building.getOrDefault("jibunAddress", "알 수 없음");
        String address = fullAddress.contains(" ") ? fullAddress.split(" ", 2)[1] : fullAddress;

        // 📌 주요 정보 추출 및 계산
        BigDecimal maintenanceFee = BigDecimal.valueOf(((Integer) itemDetail.getOrDefault("maintenanceFee", 0)) / 10);
        Integer floor = (Integer) itemDetail.getOrDefault("floor", 0);
        BigDecimal deposit = BigDecimal.valueOf(((Integer) itemDetail.getOrDefault("deposit", 0)) / 10);
        BigDecimal monthlyRent = BigDecimal.valueOf(((Integer) itemDetail.getOrDefault("monthlyRent", 0)) / 10);
        BigDecimal premium = BigDecimal.valueOf(((Integer) itemDetail.getOrDefault("premium", 0)) / 10);
        Double longitude = (Double) itemDetail.getOrDefault("longitude", 0.0);
        Double latitude = (Double) itemDetail.getOrDefault("latitude", 0.0);

        // 📌 중개사무소 이름 추출
        String agentOfficeName = null;
        Map<String, Object> userMap = (Map<String, Object>) itemDetail.get("user");
        if (userMap != null) {
            Map<String, Object> agentUserMap = (Map<String, Object>) userMap.get("agentUser");
            if (agentUserMap != null) {
                Map<String, Object> agentMap = (Map<String, Object>) agentUserMap.get("agent");
                if (agentMap != null) {
                    agentOfficeName = (String) agentMap.get("name");
                }
            }
        }

        // 📌 DTO 생성
        SanggaItemDto dto = new SanggaItemDto(
                null,  // ID는 DB에서 자동 생성될 가능성 있음
                itemId,
                address,
                floor,
                new BigDecimal("0"), // 면적 정보 없음
                deposit,
                monthlyRent,
                maintenanceFee,
                longitude,
                latitude,
                LocalDateTime.now(),
                "네모", // 플랫폼 지정
                premium // 중개사 정보 없음
        );
        dto.setAgentOfficeName(agentOfficeName);
        return dto;
    }

    public String makeNemoText(String articleType, String ad, SanggaItemDto sanggaItemDto) {
         // 📌 텍스트 생성
        String text = "";
//        BigDecimal premium = sanggaItemDto.getPremium().divide(BigDecimal.TEN);

        String officeName = sanggaItemDto.getAgentOfficeName() != null ? sanggaItemDto.getAgentOfficeName() : "알 수 없음";

        if ("store".equals(articleType)) {
            text = String.format("네모\n중개사무소 %s\n%s\n%d층\n%s/%s/%s\n권리금 %s\nhttps://www.nemoapp.kr/share/store/%s\nhttps://map.naver.com/p/search/%s",
                    officeName, sanggaItemDto.getAddress(), sanggaItemDto.getFloor(),
                    sanggaItemDto.getDeposit().toPlainString(), sanggaItemDto.getMonthlyFee().toPlainString(), sanggaItemDto.getManagementFee().toPlainString(),
                    sanggaItemDto.getPremium().toPlainString(), ad, sanggaItemDto.getAddress().replaceAll(" ", "%20"));
        } else if ("office".equals(articleType)) {
            text = String.format("네모\n중개사무소 %s\n%s\n%d층\n%s/%s/%s\n권리금 %s\nhttps://www.nemoapp.kr/share/office/%s\nhttps://map.naver.com/p/search/%s",
                    officeName, sanggaItemDto.getAddress(), sanggaItemDto.getFloor(),
                    sanggaItemDto.getDeposit().toPlainString(), sanggaItemDto.getMonthlyFee().toPlainString(), sanggaItemDto.getManagementFee().toPlainString(),
                    sanggaItemDto.getPremium().toPlainString(), ad, sanggaItemDto.getAddress().replaceAll(" ", "%20"));
        }

        return text;

    }

    private Map<String, Map<String, Object>> getItemList(String articleType, AgentCustomer agentCustomer) {
            Map<String, Map<String, Object>> nemoCurrentArticles = null;
            try {
                nemoCurrentArticles = getNemoCurrentList(articleType, agentCustomer);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }

        return nemoCurrentArticles;
    }

    public Map<String, Map<String, Object>> getNemoCurrentList(String articleType, AgentCustomer agentCustomer) throws URISyntaxException {
        Map<String, Map<String, Object>> nemoCurrentMap = new HashMap<>();
        Polygon polygon = agentCustomer.getPolygon();
        BoundingBox boundingBox = PolygonUtils.getBoundingBox(polygon);

        String ax = String.valueOf(boundingBox.getMinLng());
        String ay = String.valueOf(boundingBox.getMinLat());
        String bx = String.valueOf(boundingBox.getMaxLng());
        String by = String.valueOf(boundingBox.getMaxLat());

        // 📌 검색 카운트 URL 생성
        URI searchCountUri = new URIBuilder("https://api.nemoapp.kr/api/" + articleType + "/search-count")
                .addParameter("CompletedOnly", "false")
                .addParameter("SWLng", ax)
                .addParameter("SWLat", ay)
                .addParameter("NELng", bx)
                .addParameter("NELat", by)
                .addParameter("Zoom", "16")
                .addParameter("SortBy", "2")
                .build();

        // 📌 totalPageCount 가져오기 (WebClient 사용)
        Integer totalPageCount = webClient.get()
                .uri(searchCountUri)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> (Integer) response.get("totalPageCount"))
                .block(); // 블로킹 방식 (비동기 필요 시 Flux 사용 가능)

        if (totalPageCount == null) return nemoCurrentMap;

        // 📌 0~5 페이지까지 조회 (최대 totalPageCount)
        IntStream.rangeClosed(0, Math.min(5, totalPageCount)).forEach(pageIndex -> {
            try {
                URI articlesUri = new URIBuilder("https://api.nemoapp.kr/api/" + articleType + "/search-list")
                        .addParameter("CompletedOnly", "false")
                        .addParameter("SWLng", ax)
                        .addParameter("SWLat", ay)
                        .addParameter("NELng", bx)
                        .addParameter("NELat", by)
                        .addParameter("Zoom", "16")
                        .addParameter("SortBy", "2")
                        .addParameter("PageIndex", String.valueOf(pageIndex))
                        .build();

                // 📌 각 페이지의 데이터를 요청하고 처리
                List<Map<String, Object>> items = webClient.get()
                        .uri(articlesUri)
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .map(response -> (List<Map<String, Object>>) response.get("items"))
                        .block();

                if (items != null) {
                    items.forEach(item -> nemoCurrentMap.put(item.get("number").toString(), item));
                }
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        });

        return nemoCurrentMap;
    }
}
