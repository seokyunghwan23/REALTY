package com.realty.Realtymate.service.naverApi;

import com.fasterxml.jackson.databind.JsonNode;
import com.realty.Realtymate.model.*;

import java.util.ArrayList;
import java.util.List;

public interface NaverApiService {

    void alertHostelNewAd(ArrayList<String> previousList, List<String> dongPnu);

    void alertIamGroundNewAd(ArrayList<String> previousList, List<String> dongPnu);

    void alertSanggaNewAd(List<SanggaItemEntity> previousItemList, AgentCustomer agentCustomer);

    void alertApartNewAd(List<ApartItemEntity> previousItemList, ApartCustomer apartCustomer);

    ArrayList<String> getItemListWithCustomer(CustomerInfoEntity customerInfo);

    SanggaItemDto getItem(String articleNo);

    ArrayList<String> getItemListByCoord(AgentCustomer agentCustomer);

    // Geocoding 관련 메서드
    JsonNode getGeocode(String address);

    JsonNode getReverseGeocode(double x, double y);

    String getAddressByLatLng(double lat, double lng);

    // 좌표 범위로 매물 검색
    ArrayList<String> getItemListByCoord(double minLat, double minLng, double maxLat, double maxLng, String propertyType);

    // Static Map 이미지 생성 (마커 포함)
    byte[] getStaticMapImage(List<double[]> coordinates);

    // realtorId로 해당 중개소 매물 목록 조회
    JsonNode getArticlesByRealtorId(String realtorId);

    // 매물 상세 정보 (raw JSON 응답)
    JsonNode getArticleDetailRaw(String articleNo);

    void alertOwnerAd(List<SanggaItemEntity> previousItemList, AgentCustomer agentCustomer);
}
