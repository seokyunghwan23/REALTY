package com.realty.Realtymate.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "sangga_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class SanggaItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false, unique = true)
    private String itemId;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @Column(name = "floor")
    private Integer floor;

    @Column(name = "area", precision = 10, scale = 2)
    private BigDecimal area;

    @Column(name = "deposit", precision = 15, scale = 2)
    private BigDecimal deposit;

    @Column(name = "monthly_fee", precision = 15, scale = 2)
    private BigDecimal monthlyFee;

    @Column(name = "management_fee", precision = 15, scale = 2)
    private BigDecimal managementFee;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "updated_date", nullable = false, updatable = false, insertable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private LocalDateTime updatedDate;

    @Column(name = "platform", length = 50)
    private String platform;

    @Column(name = "region_name", length = 255)
    private String regionName;

    @Column(name = "agent_name", length = 255)
    private String agentName;
}
