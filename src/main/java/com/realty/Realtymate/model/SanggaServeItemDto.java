package com.realty.Realtymate.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SanggaServeItemDto {
    private String itemId;
    private String address;
    private int floor;
    private BigDecimal area;
    private BigDecimal deposit;
    private BigDecimal monthlyFee;
    private BigDecimal managementFee;
    private double longitude;
    private double latitude;
    private LocalDateTime updatedDate;
    private String platform;
    private String regionName;
    private String agentName;

    // ✅ 엔티티 변환 메서드 추가
    public SanggaServeItemEntity toEntity() {
        return SanggaServeItemEntity.builder()
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
}

