package com.realty.Realtymate.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class SanggaItemDto {
    private Long id;
    private String itemId;
    private String address;
    private Integer floor;
    private BigDecimal area;
    private BigDecimal dealPrice;
    private BigDecimal deposit;
    private BigDecimal monthlyFee;
    private BigDecimal managementFee;
    private Double longitude;
    private Double latitude;
    private LocalDateTime updatedDate;
    private String platform;
    private String regionName;
    private String agentName;
    private String title;
    private String description;
    private BigDecimal premium;
    private String tradeTypeName;
    private String realestateTypeName;

    // 광고검색 결과용 추가 필드
    private String hasPhoto;  // "O" or "X"
    private String url;  // 매물 URL (네이버 자체 URL)
    private String cpPcArticleUrl;  // CP 매물 URL (실제 플랫폼 URL - serve, neonet 등)
    private String registerDate;  // 등록일 (yyyy.MM.dd)
    private String moveInDate;  // 입주가능일
    private String verificationTypeName;  // "소유자", "집주인", "일반"
    private String verificationTypeCode;  // 확인 유형 코드
    private String establishRegistrationNo;  // 중개사 등록번호
    private String cpName;  // CP 이름 (네이버의 경우)
    private String articleTypeCode;
    private Integer photoCount;  // 사진 개수 (공실클럽 조회용)
    private String realtorId;  // 중개소 ID (공실클럽 조회용)
    private String agentOfficeName;  // 중개사무소 이름 (네모 API)

    public SanggaItemDto(Object o, String articleNo, String address, Integer floor, BigDecimal exclusiveSpace,BigDecimal dealPrice, BigDecimal warrantPrice, BigDecimal rentPrice, BigDecimal monthlyManagementCost, Double longitude, Double latitude, LocalDateTime now, String platform, String realtorName, String title, String description, String tradeTypeName, String realestateTypeName) {
        this.itemId = articleNo;
        this.address = address;
        this.floor = floor;
        this.area = exclusiveSpace;
        this.dealPrice = dealPrice;
        this.deposit = warrantPrice;
        this.monthlyFee = rentPrice;
        this.managementFee = monthlyManagementCost;
        this.longitude = longitude;
        this.latitude = latitude;
        this.updatedDate = now;
        this.platform = platform;
        this.agentName = realtorName;
        this.title = title;
        this.description = description;
        this.tradeTypeName = tradeTypeName;
        this.realestateTypeName = realestateTypeName;
    }

    public SanggaItemDto(Object o, String itemId, String address, Integer floor, BigDecimal area, BigDecimal deposit, BigDecimal monthlyRent, BigDecimal maintenanceFee, Double longitude, Double latitude, LocalDateTime now, String platform, BigDecimal premium) {
        this.itemId = itemId;
        this.address = address;
        this.floor = floor;
        this.area = area;
        this.deposit = deposit;
        this.monthlyFee = monthlyRent;
        this.managementFee = maintenanceFee;
        this.longitude = longitude;
        this.latitude = latitude;
        this.updatedDate = now;
        this.platform = platform;
        this.premium = premium;
    }

    public SanggaItemEntity toEntity() {
        return SanggaItemEntity.builder()
                .itemId(this.itemId)
                .address(this.address)
                .floor(this.floor)
                .area(this.area)
                .deposit(this.deposit)
                .monthlyFee(this.monthlyFee)
                .managementFee(this.managementFee)
                .longitude(this.longitude)
                .latitude(this.latitude)
                .updatedDate(this.updatedDate != null ? this.updatedDate : LocalDateTime.now()) // 값 없으면 현재 시간
                .platform(this.platform)
                .regionName(this.regionName)
                .agentName(this.agentName)
                .build();
    }

    public SanggaServeItemDto toServeDto() {
        return SanggaServeItemDto.builder()
                .itemId(this.itemId)
                .address(this.address)
                .floor(this.floor != null ? this.floor : 0)
                .area(this.area)
                .deposit(this.deposit)
                .monthlyFee(this.monthlyFee)
                .managementFee(this.managementFee)
                .longitude(this.longitude)
                .latitude(this.latitude)
                .updatedDate(this.updatedDate)
                .platform(this.platform)
                .regionName(this.regionName)
                .agentName(this.agentName)
                .build();
    }

}

