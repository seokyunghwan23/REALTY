package com.realty.Realtymate.service.RealtySerevice;

import com.realty.Realtymate.model.CustomerInfoEntity;
import com.realty.Realtymate.model.SanggaItemDto;
import com.realty.Realtymate.model.SanggaItemEntity;
import com.realty.Realtymate.repository.CustomerInfoRepository;
import com.realty.Realtymate.repository.SanggaItemRepository;
import com.realty.Realtymate.service.RepositoryService.ItemInfoService;
import com.realty.Realtymate.service.naverApi.NaverApiServiceImpl;
import com.realty.Realtymate.service.telegramApi.TelegramApiService;
import com.realty.Realtymate.utils.PolygonUtils;
import com.realty.Realtymate.utils.Utils;
import org.locationtech.jts.geom.Polygon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MatchingServiceImpl implements MatchingService {

    @Autowired
    TelegramApiService telegramApiService;
    private final CustomerInfoRepository customerInfoRepository;
    private final SanggaItemRepository sanggaItemRepository;
    @Autowired
    ItemInfoService itemInfoService;

    @Autowired
    NaverApiServiceImpl naverApiService;

    public MatchingServiceImpl(CustomerInfoRepository customerInfoRepository, SanggaItemRepository sanggaItemRepository) {
        this.customerInfoRepository = customerInfoRepository;
        this.sanggaItemRepository = sanggaItemRepository;
    }

    @Override
    public void matchCustomersWithListings() {
        List<CustomerInfoEntity> customers = customerInfoRepository.findAll();
        for (CustomerInfoEntity customer : customers) {
            if (!customer.getAlert()){
                return;
            }
            String lastId = customer.getLastId(); // 손님의 마지막 매물 ID 가져오기

            if (lastId == null || lastId.isEmpty()) {
                lastId = "0"; // 값이 없으면 가장 작은 ID부터 조회
            }
            List<SanggaItemEntity> previousItemList = sanggaItemRepository.findNewListings(lastId);

            ArrayList<String> previousList = (ArrayList<String>) previousItemList.stream()
                    .filter(previousItem -> previousItem.getPlatform().equals("네이버"))
                    .map(SanggaItemEntity::getItemId)
                    .collect(Collectors.toList());

            if (!customer.getAlert()) {
                return;
            }
            ArrayList<String> currentList = naverApiService.getItemListWithCustomer(customer);
            ArrayList<String> newAd = Utils.checkNewAd(previousList, currentList);

            newAd.forEach(articleNo -> {
                SanggaItemDto sanggaItemDto = naverApiService.getItem(articleNo);  // 📌 매물 정보 가져오기
                itemInfoService.saveSanggaItem(sanggaItemDto);  // 원본 데이터 저장
                String text = makeMatchingText(customer, sanggaItemDto);
//                System.out.println(text);
//                telegramApiService.sendMessage(customer.getChatId(), text);
            });

            String maxItemId = currentList.stream()
                    .map(Long::parseLong)                     // 문자열 → 숫자로 변환
                    .max(Long::compareTo)                     // 숫자 비교로 최대값 추출
                    .map(String::valueOf)                     // 다시 문자열로 변환
                    .orElse(null);
            customerInfoRepository.updateLastId(customer.getId(), maxItemId);
        }

 /*       List<CustomerInfoEntity> customers = customerInfoRepository.findAll();
        for (CustomerInfoEntity customer : customers) {
            if (!customer.getAlert()){
                return;
            }
            System.out.println(customer);
            String lastId = customer.getLastId(); // 손님의 마지막 매물 ID 가져오기

            if (lastId == null || lastId.isEmpty()) {
                lastId = "0"; // 값이 없으면 가장 작은 ID부터 조회
            }
            System.out.println(lastId);
            List<SanggaItemEntity> newListings = sanggaItemRepository.findNewListings(lastId);
            String maxItemId = sanggaItemRepository.findMaxItemId();
            List<SanggaItemEntity> matchingListings = newListings.stream()
                    .filter(item -> filterByFloor(item, customer.getMinFloor(), customer.getMaxFloor()))
                    .filter(item -> filterByArea(item, customer.getMinArea(), customer.getMaxArea()))
                    .filter(item -> filterByDeposit(item, customer.getMaxDeposit()))
                    .filter(item -> filterByMonthlyFee(item, customer.getMaxMonthlyFee()))
                    .filter(item -> filterByPolygon(item, customer.getPolygon()))
                    .collect(Collectors.toList());


            if (!matchingListings.isEmpty()) {
                System.out.println("🔹 손님 " + customer.getName() + "에게 새로운 매물 있음!");
                System.out.println(matchingListings.size() + "개");
                matchingListings.forEach(matching -> {
//                    telegramApiService.sendMessage("-1002180073461", makeMatchingText(customer, matching));

                });
            }
            customerInfoRepository.updateLastId(customer.getId(), maxItemId);
        }*/
    }

    private String makeMatchingText(CustomerInfoEntity customer, SanggaItemDto sanggaItem) {
        String url = "https://new.land.naver.com/offices?articleNo=" + sanggaItem.getItemId();


        return String.join("\n",
                "손님번호 : " + customer.getCustomerId() + " / " + customer.getContact(),
                "업종 : " + customer.getStoreCategory(),
                "주소 : " + sanggaItem.getAddress() + " " + sanggaItem.getFloor() + "층",
                String.format("%d/%d/%d",
                        sanggaItem.getDeposit().intValue(),
                        sanggaItem.getMonthlyFee().intValue(),
                        sanggaItem.getManagementFee().intValue())
                ,
                url,
                "https://map.naver.com/p/search/" + sanggaItem.getAddress().replace(" ", "%20")
        );
    }

    private boolean filterByFloor(SanggaItemEntity item, Integer minFloor, Integer maxFloor) {
        if (item.getFloor() == null) return true;
        if (minFloor != null && item.getFloor() < minFloor) return false;
        if (maxFloor != null && item.getFloor() > maxFloor) return false;
        return true;
    }

    // 🔹 면적 필터링
    private boolean filterByArea(SanggaItemEntity item, BigDecimal minArea, BigDecimal maxArea) {
        if (minArea != null && item.getArea().compareTo(minArea) < 0) return false;
        if (maxArea != null && item.getArea().compareTo(maxArea) > 0) return false;
        return true;
    }
    // 🔹 Polygon 필터링 (매물의 위/경도가 손님의 관심 지역에 포함되는지 확인)

    private boolean filterByPolygon(SanggaItemEntity item, Polygon customerPolygon) {
        if (customerPolygon == null) return true; // Polygon이 없으면 필터링 제외
        return PolygonUtils.isInsidePolygon(customerPolygon, item.getLongitude(), item.getLatitude());
    }


    // 🔹 보증금 필터링
    private boolean filterByDeposit(SanggaItemEntity item, BigDecimal maxDeposit) {
        if (maxDeposit != null && item.getDeposit().compareTo(maxDeposit) > 0) return false;
        return true;
    }

    // 🔹 월세 필터링
    private boolean filterByMonthlyFee(SanggaItemEntity item, BigDecimal maxMonthlyFee) {
        if (maxMonthlyFee != null && item.getMonthlyFee().compareTo(maxMonthlyFee) > 0) return false;
        return true;
    }

    // 🔹 Polygon 필터링 (매물의 위/경도가 손님의 관심 지역에 포함되는지 확인)
}
