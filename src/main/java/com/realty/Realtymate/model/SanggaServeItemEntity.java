package com.realty.Realtymate.model;

import lombok.*;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;


@Entity
@Table(
        name = "sangga_serve_items",
        uniqueConstraints = @UniqueConstraint(
                name = "unique_sangga_item",
                columnNames = {"address", "floor", "deposit", "monthly_fee", "management_fee"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SanggaServeItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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
}

