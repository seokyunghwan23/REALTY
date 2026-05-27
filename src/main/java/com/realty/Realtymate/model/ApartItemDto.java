package com.realty.Realtymate.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ApartItemDto {
    private Long id;
    private String itemId;
    private String address;
    private String dong;
    private String floor;
    private String ho;
    private BigDecimal area;
    private BigDecimal dealPrice;
    private BigDecimal deposit;
    private BigDecimal monthlyFee;
    private BigDecimal managementFee;
    private String tradeType;
    private LocalDateTime updatedDate;
    private String apartName;
    private String agentName;
}
