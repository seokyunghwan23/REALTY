package com.realty.Realtymate.service.zigbangApi;

import com.realty.Realtymate.model.AgentCustomer;
import com.realty.Realtymate.model.BoundingBox;
import com.realty.Realtymate.model.DagaguItemEntity;
import com.realty.Realtymate.service.RepositoryService.DagaguItemService;
import com.realty.Realtymate.service.telegramApi.TelegramApiService;
import com.realty.Realtymate.utils.KakaoService;
import com.realty.Realtymate.utils.PolygonUtils;
import com.realty.Realtymate.utils.Utils;
import org.locationtech.jts.geom.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ZigbangApiServiceImpl implements ZigbangApiService {
    private final WebClient webClient;
    @Autowired
    DagaguItemService dagaguItemService;
    @Autowired
    TelegramApiService telegramApiService;

    KakaoService kakaoService = new KakaoService(WebClient.builder());

    public ZigbangApiServiceImpl() {
        this.webClient = WebClient.builder()
                .baseUrl("https://apis.zigbang.com")
                .defaultHeader("Cookie", "") // 쿠키 비활성화
                .build();
    }

    @Override
    public void alertDagaguNewAd(List<DagaguItemEntity> previousItemList, AgentCustomer agentCustomer) {
        //이전매물 받아오기
        ArrayList<String> previousList = (ArrayList<String>) previousItemList.stream()
                .filter(previousItem -> previousItem.getPlatform().equals("직방"))
                .map(DagaguItemEntity::getItemId)
                .collect(Collectors.toList());

        //현재매물 받아오기
        ArrayList<String> currentList = getItemList(agentCustomer);
        ArrayList<String> newAd = Utils.checkNewAd(previousList, currentList);

        //DB에 저장
        newAd.forEach(itemId -> {
            Map<String, Object> zigbangData = getItem(itemId);
            var item = Optional.ofNullable((Map<String, Object>) zigbangData.get("item")).orElse(Collections.emptyMap());
            var location = Optional.ofNullable((Map<String, Object>) item.get("location")).orElse(Collections.emptyMap());
            double lat = ((Number) location.get("lat")).doubleValue();
            double lng = ((Number) location.get("lng")).doubleValue();

            if (PolygonUtils.isInsidePolygon(agentCustomer.getPolygon(), lng, lat)){
                String text = makeZigbangText(zigbangData);
                telegramApiService.sendMessage(agentCustomer, text);
                dagaguItemService.addDagaguItem(itemId, agentCustomer.getAgentName(), "직방", agentCustomer.getKind());
            }
        });
    }

    private String makeZigbangText(Map<String, Object> zigbangItem) {
        var agent = Optional.ofNullable((Map<String, Object>) zigbangItem.get("agent")).orElse(Collections.emptyMap());
        var item = Optional.ofNullable((Map<String, Object>) zigbangItem.get("item")).orElse(Collections.emptyMap());
        var location = Optional.ofNullable((Map<String, Object>) item.get("location")).orElse(Collections.emptyMap());
        double lat = ((Number) location.get("lat")).doubleValue();
        double lng = ((Number) location.get("lng")).doubleValue();
        var id = (Integer) item.get("itemId");

        var address = kakaoService.getKakaoAddress(lng, lat);
        var price = Optional.ofNullable((Map<String, Object>) item.get("price")).orElse(Collections.emptyMap());
        var deposit = Optional.ofNullable((Integer) price.get("deposit")).orElse(0);
        var rent = Optional.ofNullable((Integer) price.get("rent")).orElse(0);
        var manageCost = Optional.ofNullable((Map<String, Object>) item.get("manageCost")).orElse(Collections.emptyMap());
        var serviceType = Optional.ofNullable((String) item.get("serviceType")).orElse("oneroom");
        var kind = serviceType.equals("빌라") ? "villa" : "oneroom";

        // 관리비 amount 처리 (패턴 매칭 적용)
        double amount = Optional.ofNullable(manageCost.get("amount"))
                .map(value -> {
                    if (value instanceof Double d) {
                        return d;
                    } else if (value instanceof Integer i) {
                        return i.doubleValue();
                    } else {
                        return 0.0;
                    }
                }).orElse(0.0);


        var floor = Optional.ofNullable((Map<String, Object>) item.get("floor")).orElse(Collections.emptyMap());
        var floorStr = Optional.ofNullable((String) floor.get("floor")).orElse("0");

        var agentTitle = Optional.ofNullable((String) agent.get("agentTitle")).orElse("알 수 없음");

        return String.format(
                "직방\n%s\n%s %s층\n%d/%d/%.0f\n%s\nhttps://map.naver.com/p/search/%s",
                agentTitle,
                address,
                floorStr,
                deposit,
                rent,
                amount,
                "https://m.zigbang.com/home/" + kind + "/items/" + id,
                address.replaceAll(" ", "%20")
        );
    }

    private ArrayList<String> getItemList(AgentCustomer agentCustomer) {
        Polygon polygon = agentCustomer.getPolygon();
        BoundingBox boundingBox = PolygonUtils.getBoundingBox(polygon);

        Map<String, Object> params = new HashMap<>();
        params.put("geohash", "wydm0");
        params.put("depositMin", 0);
        params.put("rentMin", 0);
        params.put("salesTypes[0]", "월세");
        params.put("lngEast", boundingBox.getMaxLng());
        params.put("lngWest", boundingBox.getMinLng());
        params.put("latSouth", boundingBox.getMinLat());
        params.put("latNorth", boundingBox.getMaxLat());
        params.put("domain", "zigbang");
        params.put("checkAnyItemWithoutFilter", true);

        String paramString = Utils.mapToUrlParams(params);
        String endpoint = agentCustomer.getKind().equals("원룸") ? "/v2/items/oneroom" : "/v2/items/villa";

        Map<String, Object> response = webClient.get()
                .uri(endpoint + "?" + paramString)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .block();
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");

        ArrayList<String> itemList = new ArrayList<>();
        items.forEach(item -> {
            Double lng = (Double) item.get("lng");
            Double lat = (Double) item.get("lat");

            if (lng != null && lat != null && lng >= boundingBox.getMinLng() && lng <= boundingBox.getMaxLng() && lat >= boundingBox.getMinLat() && lat <= boundingBox.getMaxLat()) {
                itemList.add(item.get("itemId").toString());
            }
        });

        return itemList;
    }

    public Map<String, Object> getItem(String itemId) {
        String url = "/v3/items/" + itemId + "?version=&domain=zigbang";

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(Map.class)
                .block(); // 동기적으로 처리 (필요하면 비동기 코드로 변경 가능)
    }
}
