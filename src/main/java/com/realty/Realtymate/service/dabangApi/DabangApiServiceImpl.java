package com.realty.Realtymate.service.dabangApi;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realty.Realtymate.model.AgentCustomer;
import com.realty.Realtymate.model.BoundingBox;
import com.realty.Realtymate.model.DagaguItemEntity;
import com.realty.Realtymate.service.RepositoryService.DagaguItemService;
import com.realty.Realtymate.service.telegramApi.TelegramApiService;
import com.realty.Realtymate.utils.KakaoService;
import com.realty.Realtymate.utils.PolygonUtils;
import com.realty.Realtymate.utils.Utils;
import org.apache.hc.core5.net.URIBuilder;
import org.locationtech.jts.geom.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Map;

@Service
public class DabangApiServiceImpl implements DabangApiService {
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired
    DagaguItemService dagaguItemService;
    @Autowired
    TelegramApiService telegramApiService;

    KakaoService kakaoService = new KakaoService(WebClient.builder());

    public DabangApiServiceImpl(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public void alertDagaguNewAd(List<DagaguItemEntity> previousItemList, AgentCustomer agentCustomer) {
        //이전매물 받아오기
        ArrayList<String> previousList = (ArrayList<String>) previousItemList.stream()
                .filter(previousItem -> previousItem.getPlatform().equals("다방"))
                .map(DagaguItemEntity::getItemId)
                .collect(Collectors.toList());

        //현재매물 받아오기
        ArrayList<String> currentList = getItemList(agentCustomer);
        ArrayList<String> newAd = Utils.checkNewAd(previousList, currentList);


        newAd.forEach(itemId -> {
            Map<String, Object> dabangData = getItem(itemId);
            var room = (Map<String, Object>) dabangData.get("room");
            var location = (List<Double>) room.get("location");

            var lat = location.get(1);
            var lng = location.get(0);

            if(PolygonUtils.isInsidePolygon(agentCustomer.getPolygon(), lng, lat)) {
                String text = makeDabangText(dabangData);
                telegramApiService.sendMessage(agentCustomer, text);
                dagaguItemService.addDagaguItem(itemId, agentCustomer.getAgentName(), "다방", agentCustomer.getKind());
            }
        });

    }

    private ArrayList<String> getItemList(AgentCustomer agentCustomer) {
        Polygon polygon = agentCustomer.getPolygon();
        BoundingBox boundingBox = PolygonUtils.getBoundingBox(polygon);
        String kind = agentCustomer.getKind();

        ArrayList<String> idList = new ArrayList<>();

        // 원룸: one-two API만 호출
        if (kind.equals("원룸")) {
            idList.addAll(getOneTwoCurrentList(boundingBox, "ONE_ROOM"));
        } else if (kind.equals("투룸")) {
            idList.addAll(getOneTwoCurrentList(boundingBox, "TWO_ROOM"));
            idList.addAll(getHouseVillaCurrentList(boundingBox));
        }
        return idList;
    }

    /**
     * one-two API 호출 (원룸, 투룸)
     */
    private ArrayList<String> getOneTwoCurrentList(BoundingBox boundingBox, String roomKind) {
        return fetchRoomList("one-two", boundingBox, List.of(roomKind));
    }

    /**
     * house-villa API 호출 (투룸인 경우만 실행)
     */
    private ArrayList<String> getHouseVillaCurrentList(BoundingBox boundingBox) {
        List<String> roomKind = List.of("THREE_ROOM", "FOUR_ROOM");
        return fetchRoomList("house-villa", boundingBox, roomKind);
    }

    /**
     * 다방 API 호출을 공통 처리하는 메서드
     */
    public ArrayList<String> fetchRoomList(String category, BoundingBox boundingBox, List<String> roomKind) {
        ArrayList<String> idList = new ArrayList<>();

        for (String rk : roomKind) {
            String bbox = String.format("{\"sw\":{\"lat\":%s,\"lng\":%s},\"ne\":{\"lat\":%s,\"lng\":%s}}",
                    boundingBox.getMinLat(), boundingBox.getMinLng(),
                    boundingBox.getMaxLat(), boundingBox.getMaxLng());

            String filters;
            if ("one-two".equals(category)) {  // 원룸 필터 적용
                filters = "{\"sellingTypeList\":[\"MONTHLY_RENT\"],"
                        + "\"depositRange\":{\"min\":0,\"max\":999999},"
                        + "\"priceRange\":{\"min\":0,\"max\":999999},"
                        + "\"isIncludeMaintenance\":false,"
                        + "\"pyeongRange\":{\"min\":0,\"max\":999999},"
                        + "\"useApprovalDateRange\":{\"min\":0,\"max\":999999},"
                        + "\"roomFloorList\":[\"GROUND_FIRST\",\"GROUND_SECOND_OVER\",\"SEMI_BASEMENT\",\"ROOFTOP\"],"
                        + "\"roomTypeList\":[\"" + rk + "\"],"  // ✅ roomTypeList 사용
                        + "\"dealTypeList\":[\"AGENT\",\"DIRECT\"],"
                        + "\"canParking\":false,"
                        + "\"isShortLease\":false,"
                        + "\"hasElevator\":false,"
                        + "\"hasPano\":false,"
                        + "\"isDivision\":false,"
                        + "\"isDuplex\":false}";
            } else {  // 투룸, 쓰리룸 등 다른 경우 적용
                filters = "{\"sellingTypeList\":[\"MONTHLY_RENT\"],"
                        + "\"tradeRange\":{\"min\":0,\"max\":999999},"  // ✅ tradeRange 포함
                        + "\"depositRange\":{\"min\":0,\"max\":999999},"
                        + "\"priceRange\":{\"min\":0,\"max\":999999},"
                        + "\"isIncludeMaintenance\":false,"
                        + "\"pyeongRange\":{\"min\":0,\"max\":999999},"
                        + "\"useApprovalDateRange\":{\"min\":0,\"max\":999999},"
                        + "\"roomFloorList\":[\"GROUND_FIRST\",\"GROUND_SECOND_OVER\",\"SEMI_BASEMENT\",\"ROOFTOP\"],"
                        + "\"dealTypeList\":[\"AGENT\",\"DIRECT\"],"
                        + "\"canParking\":false,"
                        + "\"isShortLease\":false,"
                        + "\"hasElevator\":false,"
                        + "\"hasPano\":false,"
                        + "\"roomCount\":\"" + rk + "\"}";  // ✅ roomCount 사용
            }


            // 필수: URL 인코딩 적용

            Integer page = 1; // 첫 페이지

            String baseUrl = "https://www.dabangapp.com/api/v5/room-list/category/";
            while (true) {
                URI uri;
                try {
                    uri = new URIBuilder(baseUrl + category + "/bbox")
                            .addParameter("filters", filters)
                            .addParameter("bbox", bbox)
                            .addParameter("page", page.toString())
                            .addParameter("useMap", "naver")
                            .addParameter("zoom", "15")
                            .build();
                } catch (URISyntaxException e) {
                    throw new RuntimeException("Invalid URI", e);
                }

                // WebClient를 사용하여 GET 요청
                Map<String, Object> response = webClient.get()
                        .uri(uri)
                        .headers(headers -> {
                            headers.set(HttpHeaders.ACCEPT, "application/json, text/plain, */*");
                            headers.set("d-api-version", "5.0.0");
                            headers.set("d-app-version", "1");
                            headers.set("d-call-type", "web");
                            headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36");
                        })
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();


                if (response == null || !response.containsKey("result")) {
                    throw new RuntimeException("API 응답이 올바르지 않습니다.");
                }

                Map result = (Map) response.get("result");
                ArrayList<Map> roomList = (ArrayList<Map>) result.get("roomList");

                for (Map room : roomList) {
                    String id = (String) room.get("id");
                    idList.add(id);
                }

                Boolean hasMore = (Boolean) result.get("hasMore");
                if (!hasMore) {
                    break;
                }
                page++; // 다음 페이지 요청
            }
        }
        return idList;
    }

    // 아이템 상세 정보 조회
    public Map<String, Object> getItem(String itemId) {
        String url = "https://www.dabangapp.com/api/3/new-room/detail?api_version=3.0.1&version=1&call_type=web&room_id=" + itemId;
        URI uri = null;
        try {
            uri = new URIBuilder(url).build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        String response = webClient.get()
                .uri(uri)
                .headers(headers -> {
                    headers.set(HttpHeaders.ACCEPT, "application/json, text/plain, */*");
                    headers.set("d-api-version", "5.0.0");
                    headers.set("d-app-version", "1");
                    headers.set("d-call-type", "web");
                    headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36");
                })
                .retrieve()
                .bodyToMono(String.class)
                .block(); // 동기 처리

        try {
            return objectMapper.readValue(response, Map.class); // JSON 변환
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public String makeDabangText(Map<String, Object> dabangItem) {
        var agent = (Map<String, Object>) dabangItem.get("agent");
        var room = (Map<String, Object>) dabangItem.get("room");
        var location = (List<Double>) room.get("location");

        var lat = location.get(1);
        var lng = location.get(0);

        var maintenanceCost = Optional.ofNullable((Integer) room.get("maintenance_cost"))
                .orElse(0) / 10_000;

        var priceTitle = (String) room.get("price_title");
        var ho = (String) room.get("ho");
        var id = (String) room.get("id");
        var name = Optional.ofNullable(agent)
                .map(a -> (String) a.get("name"))
                .orElse("직거래");

        var address = "";
        address = kakaoService.getKakaoAddress(lng, lat);

        return String.join("\n",
                "다방",
                name,
                address + " " + ho,
                String.format("%s / %d", priceTitle, maintenanceCost),
                "https://www.dabangapp.com/room/" + id,
                "https://map.naver.com/p/search/" + address.replace(" ", "%20")
        );
    }
}
