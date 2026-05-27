package com.realty.Realtymate.service.peterpanzApi;
import com.realty.Realtymate.model.AgentCustomer;
import com.realty.Realtymate.model.BoundingBox;
import com.realty.Realtymate.model.DagaguItemEntity;
import com.realty.Realtymate.service.RepositoryService.DagaguItemService;
import com.realty.Realtymate.service.telegramApi.TelegramApiService;
import com.realty.Realtymate.utils.KakaoService;
import com.realty.Realtymate.utils.PolygonUtils;
import com.realty.Realtymate.utils.Utils;
import org.locationtech.jts.geom.Polygon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PeterpanzApiServiceImpl implements PeterpanzApiService{
    private final WebClient webClient;
    @Autowired
    DagaguItemService dagaguItemService;
    @Autowired
    TelegramApiService telegramApiService;

    KakaoService kakaoService = new KakaoService(WebClient.builder());

    public PeterpanzApiServiceImpl(WebClient webClient) {
        this.webClient = WebClient.builder()
                .baseUrl("https://apis.zigbang.com")
                .defaultHeader("Cookie", "") // 쿠키 비활성화
                .build();
    }
    @Override
    public void alertDagaguNewAd(List<DagaguItemEntity> previousItemList, AgentCustomer agentCustomer) {
        ArrayList<String> previousList = (ArrayList<String>) previousItemList.stream()
                .filter(previousItem -> previousItem.getPlatform().equals("피터팬"))
                .map(DagaguItemEntity::getItemId)
                .collect(Collectors.toList());

        ArrayList<String> currentList = getItemList(agentCustomer);
        ArrayList<String> newAd = Utils.checkNewAd(previousList, currentList);
        //DB에 저장
        newAd.forEach(itemId -> {
            Map<String, Object> peterpanzData = getItem(itemId);
            String text = makePeterpanzText(peterpanzData);
            telegramApiService.sendMessage(agentCustomer, text);
            dagaguItemService.addDagaguItem(itemId, agentCustomer.getAgentName(), "피터팬", agentCustomer.getKind());
        });
    }


    private ArrayList<String> getItemList(AgentCustomer agentCustomer){
        Polygon polygon = agentCustomer.getPolygon();
        BoundingBox boundingBox = PolygonUtils.getBoundingBox(polygon);

        String url = null;
        if (agentCustomer.getKind().equals("원룸")) {
            url = "https://api.peterpanz.com/houses/markers?zoomLevel=16&dong=신림동&gungu=관악구&filter=latitude:" + boundingBox.getMinLat() + "~" + boundingBox.getMaxLat() + "||longitude:" + boundingBox.getMinLng() + "~" + boundingBox.getMaxLng() + "||contractType;[\"월세\"]||buildingType;[\"빌라/주택\"]||roomType;[\"오픈형 원룸\",\"분리형 원룸\"]&filter_version=5.1&pageSize=99999&pageIndex=1";
        } else{
            url = "https://api.peterpanz.com/houses/markers?zoomLevel=16&dong=신림동&gungu=관악구&filter=latitude:" + boundingBox.getMinLat() + "~" + boundingBox.getMaxLat() + "||longitude:" + boundingBox.getMinLng() + "~" + boundingBox.getMaxLng() + "||contractType;[\"월세\"]||buildingType;[\"빌라/주택\"]||roomType;[\"투룸\",\"쓰리룸 이상\"]&filter_version=5.1&pageSize=99999&pageIndex=1";
        }

        var allList = Optional.ofNullable(
                webClient.get()
                        .uri(url)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                        .block()
        ).orElse(List.of());  // 불변 리스트 반환

// ArrayList로 변환하고 싶다면, 아래처럼 새 ArrayList를 만들면 됩니다.
        ArrayList<String> resultList = (ArrayList<String>) allList.stream()
                .filter(item -> {
                    Map<String, Object> location = (Map<String, Object>) item.get("location");
                    Map<String, Object> coordinate = (Map<String, Object>) location.get("coordinate");

                    Double latitude = Double.parseDouble(coordinate.get("latitude").toString());
                    Double longitude = Double.parseDouble(coordinate.get("longitude").toString());
                    // 좌표가 다각형 안에 있으면 true
                    return PolygonUtils.isInsidePolygon(agentCustomer.getPolygon(), longitude, latitude);
                })
                .map(item -> item.get("hidx").toString())  // 필터링 후 hidx 값만 추출
                .collect(Collectors.toList());  // 최종 결과 리스트

        return resultList;
    }
    private String makePeterpanzText(Map<String, Object> peterpanzItem) {
        var house = (Map<String, Object>) peterpanzItem.getOrDefault("house", Collections.emptyMap());
        var agency = (Map<String, Object>) peterpanzItem.getOrDefault("agency", Collections.emptyMap());

        // 중개사 이름 설정 (없으면 "직거래")
        var agencyName = agency.isEmpty() ? "직거래" : (String) agency.get("agency_name");

        // 위치 정보
        double latitude = Optional.ofNullable((String) house.get("latitude"))
                .map(Double::parseDouble)
                .orElse(0.0);

        double longitude = Optional.ofNullable((String) house.get("longitude"))
                .map(Double::parseDouble)
                .orElse(0.0);

        // 기타 정보
        var hidx = Optional.ofNullable((Integer) house.get("hidx")).orElse(0);
        var ho = Optional.ofNullable((String) house.get("address3")).orElse("");
        var deposit = Optional.ofNullable(house.get("deposit")).map(Object::toString).orElse("").replaceAll("0000", "");
        var monthlyFee = Optional.ofNullable(house.get("monthly_fee")).map(Object::toString).orElse("").replaceAll("0000", "");
        var maintenanceCost = Optional.ofNullable(house.get("maintenance_cost")).map(Object::toString).orElse("").replaceAll("0000", "");

        // 주소 변환
        var address = kakaoService.getKakaoAddress(longitude, latitude);

        return String.format(
                "피터팬\n%s\n%s %s\n%s/%s/%s\nhttps://www.peterpanz.com/house/%d\nhttps://map.naver.com/p/search/%s",
                agencyName, address, ho, deposit, monthlyFee, maintenanceCost, hidx, address.replaceAll(" ", "%20")
        );
    }

    public Map getItem(String hidx) {
        // WebClient 객체 생성
        WebClient webClient = WebClient.create("https://api.peterpanz.com");

        // WebClient로 요청을 보내고 응답을 Map으로 받음
        Map house = webClient.get()
                .uri("/get_detail/" + hidx)  // hidx 값을 URI에 포함
                .retrieve()  // 요청 실행
                .bodyToMono(Map.class)  // 응답을 Map으로 변환
                .block();  // 동기적으로 결과를 받기 위해 block()

        return house;
    }


}
