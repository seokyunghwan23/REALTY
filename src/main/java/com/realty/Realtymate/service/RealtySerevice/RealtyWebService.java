package com.realty.Realtymate.service.RealtySerevice;

import com.realty.Realtymate.model.OwnerDetailDto;
import com.realty.Realtymate.model.SanggaItemDto;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public interface RealtyWebService {
    Mono<String> getAddressByUrl(String url);

    /**
     * 매물 검색
     * @param address 검색할 주소
     * @param floor 층 (선택사항)
     * @param propertyType 매물 종류 (주택/상가)
     * @param platforms 플랫폼 필터 (네이버, 네모, 직방, 다방, 피터팬)
     * @param transactions 거래 방식 (매매, 전세, 월세)
     * @return 검색 결과 리스트
     */
    Mono<List<SanggaItemDto>> searchAd(
            String address,
            String floor,
            String propertyType,
            Map<String, Boolean> platforms,
            Map<String, Boolean> transactions
    );

    /**
     * 소유자 상세 정보 조회
     */
    Mono<OwnerDetailDto> getOwnerDetail(
            String url,
            String platform,
            String verificationTypeName,
            String verificationTypeCode,
            String establishRegistrationNo,
            String address
    );

    /**
     * URL/매물번호로 소유자 상세 정보 조회 및 Google Sheets 저장
     * (NaverApiService로 먼저 매물 정보 조회 후 getOwnerDetail 호출)
     * @param url 매물 URL 또는 매물번호 (숫자만, fin URL, articleNo= URL 모두 지원)
     * @param employeeName 직원명
     * @param myProperties 내 매물 조회 여부
     * @param authorization Bearer 토큰 (내 매물 조회 시 필요)
     */
    Mono<Map<String, Object>> getOwnerDetailByUrl(String url, String employeeName, boolean myProperties, String authorization);
}
